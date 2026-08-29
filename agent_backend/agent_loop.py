"""Hermes-style agent turn loop for the Minecraft companion bridge.

Faithful port of the core Hermes agent execution cycle, adapted to the
asynchronous Java WebSocket bridge:

  request(user, text)          -> begin a turn
    prefetch (memory snapshot)   already frozen in the system prompt
    compress if context too long
    loop (up to max_iterations):
      LLM complete(messages, tools)
      parse tool calls
      if no calls -> reply and end turn
      append assistant tool_calls
      dispatch:
        - 'memory'   -> local MemoryStore, append result, continue loop
        - chat_public -> terminal, end turn after result
        - other      -> send to Java, wait for result(), then continue
    end turn -> sync memory (durable already) + persist session transcript

Retry: Java tool failures retry up to MAX_RETRIES. Memory consolidation
failures are counted per turn and capped (Hermes _MAX_CONSOLIDATION_FAILURES).
"""

import json
import logging
import uuid
import re
import time
from pathlib import Path

from .llm_client import parse_calls
from .memory import MemoryStore, memory_tool_definition, handle_memory_tool_call

SYSTEM = """You are a Minecraft companion AI running inside a Fabric client. Your name is "伙伴" (Buddy). You control the player through registered tools. You are helpful, concise, and safety-conscious.

## Identity
- You are a cooperative survival companion, not a speedrunner.
- You stay near your owner and help with small tasks.
- You report danger honestly and stop immediately when asked.
- You speak in the same language your owner uses.

## Tool Rules
1. Prefer "altoclef_task" for any player-relative movement or resource task. It is the ONLY tool that knows the owner's real in-game entity and can pathfind safely.
2. Use "observe_world" before any unfamiliar action. The observation is authoritative.
3. Use "baritone_goal" ONLY when exact x, y, z coordinates are supplied.
4. Raw "move", "look", "press_key", "release_key" are for short, explicit physical interactions AFTER observing the world.
5. Use "chat_public" to report results or ask for clarification.
6. Use "memory" to remember durable facts about this world and the owner across sessions.

## Action Rules
- Never output prose, XML, Markdown fences, or unknown fields.
- When native tool_calls are available, use them. Otherwise return exactly one JSON object.
- After every tool result, decide whether to continue, verify, or report.
- If a tool fails, try an alternative approach or report the failure.
- COLLECT/CRAFT/SMELT/GIVE tasks are queued in Java and run to completion. Do NOT follow up with raw movement -- the task owns the pathfinding.
- After a direct command (collect, follow, come, etc.), do NOT emit extra tool calls.

## Examples
user: 过来
assistant: {"tool":"altoclef_task","arguments":{"command":"come"}}

user: follow me
assistant: {"tool":"altoclef_task","arguments":{"command":"follow"}}

user: get one oak log
assistant: {"tool":"altoclef_task","arguments":{"command":"collect oak_log 1"}}

user: what do you see around you?
assistant: {"tool":"observe_world","arguments":{}}
"""

_DIRECT_COMMANDS = {
    "come": "come", "come here": "come", "过来": "come", "来我这里": "come",
    "follow": "follow", "follow me": "follow", "跟随": "follow", "跟着我": "follow",
    "protect": "protect", "保护我": "protect", "stop": "stop", "停止": "stop",
    "status": "status", "状态": "status", "queue": "queue", "队列": "queue",
    "橡木原木": "collect oak_log 1", "砍树": "collect oak_log 1", "砍木头": "collect oak_log 1",
}

_CATALOG_ALIASES = {}

# Hermes-style bounds
_MAX_MESSAGE_HISTORY = 60          # transcript cap before we start dropping
_COMPRESS_THRESHOLD = 32           # messages before running context compression
_MAX_ITERATIONS = 8                # max assistant loops per turn
_MAX_RETRIES = 2                   # Java tool failure retry budget
_MAX_CONSOLIDATION_FAILURES = 3    # memory consolidation attempts per turn


def direct_companion_call(text):
    """Map only unambiguous companion requests to the existing Java task surface."""
    normalized = " ".join(text.strip().lower().split())
    if normalized.startswith("ai "):
        normalized = normalized[3:].strip()
    command = _DIRECT_COMMANDS.get(normalized)
    if command:
        return {"tool": "altoclef_task", "arguments": {"command": command}}
    match = re.fullmatch(r"(collect|craft|smelt|give|attack)\s+([a-z0-9_:-]+)\s+([1-9][0-9]?)", normalized)
    if match and int(match.group(3)) <= 64:
        verb, item, count = match.groups()
        catalogue_item = _CATALOG_ALIASES.get(item, item)
        return {"tool": "altoclef_task", "arguments": {"command": f"{verb} {catalogue_item} {count}"}}
    match = re.fullmatch(r"goto\s+(-?\d+)\s+(-?\d+)\s+(-?\d+)", normalized)
    if match:
        return {"tool": "altoclef_task", "arguments": {"command": normalized}}
    return None


def normalize_tool_call(call):
    """Normalize model-produced companion commands before crossing the Java bridge."""
    if not isinstance(call, dict):
        return call
    if call.get("tool") != "altoclef_task" or not isinstance(call.get("arguments"), dict):
        return call
    command = call["arguments"].get("command", "")
    match = re.fullmatch(r"(collect|craft|smelt|give|attack)\s+([a-z0-9_:-]+)\s+([1-9][0-9]?)", command.strip().lower())
    if match:
        verb, item, count = match.groups()
        item = _CATALOG_ALIASES.get(item, item)
        return {"tool": "altoclef_task", "arguments": {"command": f"{verb} {item} {count}"}}
    return call


def _summarize_observation(obs):
    if not isinstance(obs, dict):
        return str(obs)
    parts = []
    health, food = obs.get("health", 20), obs.get("food", 20)
    parts.append(f"HP={health:.0f} food={food}")
    dim = obs.get("dimension", "")
    if dim:
        parts.append(f"dim={dim.split(':')[-1]}")
    x, y, z = obs.get("x", 0), obs.get("y", 0), obs.get("z", 0)
    parts.append(f"pos=({x:.0f},{y:.0f},{z:.0f})")
    if obs.get("in_lava"):
        parts.append("IN_LAVA")
    if obs.get("on_fire"):
        parts.append("ON_FIRE")
    if obs.get("submerged"):
        parts.append("underwater")
    hostiles = obs.get("nearby_hostiles", [])
    if hostiles:
        names = {}
        for h in hostiles[:8]:
            n = h.get("name", "?")
            names[n] = names.get(n, 0) + 1
        parts.append("hostiles:" + ",".join(f"{k}x{v}" for k, v in names.items()))
    return " | ".join(parts)


class AgentLoop:
    def __init__(self, llm, send, java_tools, on_event=None, memory_dir=None, session_file=None):
        self.llm = llm
        self.send = send
        self.on_event = on_event
        self.log = logging.getLogger("agent")

        # Memory (Hermes MEMORY.md / USER.md)
        self.memory_store = MemoryStore(memory_dir or Path("agent/memories"))
        self.memory_store.load_from_disk()

        # Tool surface = Java bridge tools + built-in memory tool
        self.all_tools = list(java_tools or []) + [memory_tool_definition()]

        # Conversation transcript (system prompt + frozen memory snapshot + turns)
        self.messages = []
        self._build_system_prompt()

        # Session persistence
        self.session_file = Path(session_file) if session_file else None
        if self.session_file:
            self._load_session()

        # Turn state
        self._turn_active = False
        self._turn_user = None
        self._turn_iterations = 0
        self._turn_consolidation_failures = 0
        self._pending = {}  # in-flight Java tool calls: id -> metadata
        self.latest_observation = {}
        self.memory = {}   # live snapshot of latest state (for observe() diagnostics)

    # -- system prompt + session --------------------------------------------

    def _build_system_prompt(self):
        memory_block = self.memory_store.system_prompt_block()
        prompt = SYSTEM
        if memory_block:
            prompt += "\n\n## Durable Memory (frozen this session)\n" + memory_block
        self.messages = [{"role": "system", "content": prompt}]

    def _load_session(self):
        if not self.session_file or not self.session_file.exists():
            return
        try:
            data = json.loads(self.session_file.read_text(encoding="utf-8"))
            history = data.get("messages", [])
            if isinstance(history, list) and history:
                # Keep the freshly-built system prompt; resume the rest.
                self.messages = self.messages[:1] + history[-_MAX_MESSAGE_HISTORY:]
                self.log.info("session resumed messages=%d", len(self.messages))
        except Exception as exc:
            self.log.warning("could not load session: %s", exc)

    def _save_session(self):
        if not self.session_file:
            return
        try:
            self.session_file.parent.mkdir(parents=True, exist_ok=True)
            payload = {"saved_at": time.time(), "messages": self.messages[1:]}
            tmp = self.session_file.with_suffix(".json.tmp")
            tmp.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
            tmp.replace(self.session_file)
        except Exception as exc:
            self.log.warning("could not save session: %s", exc)

    # -- context compression -------------------------------------------------

    def _should_compress(self):
        return len(self.messages) > _COMPRESS_THRESHOLD

    def _compress(self):
        """Summarize older messages into a system summary, keeping recent turns."""
        if not self._should_compress():
            return
        keep = self.messages[-12:]
        older = self.messages[1:-12]
        if len(older) < 2:
            return
        try:
            summary_prompt = [{
                "role": "system",
                "content": "Summarize the following conversation history into a compact, "
                           "bullet-point summary that preserves factual decisions, the owner's "
                           "requests, world state, and task outcomes. Do not invent anything.",
            }, {
                "role": "user",
                "content": json.dumps(older, ensure_ascii=False),
            }]
            response = self.llm.complete(summary_prompt, [])
            text = self._response_text(response)
            if not text:
                return
            summary = {"role": "system", "content": "EARLIER CONVERSATION SUMMARY:\n" + text}
            self.messages = self.messages[:1] + [summary] + keep
            self.log.info("context compressed messages=%d", len(self.messages))
            self.emit({"kind": "compression", "remaining_messages": len(self.messages)})
        except Exception as exc:
            self.log.warning("compression failed: %s", exc)

    # -- helpers -------------------------------------------------------------

    @staticmethod
    def _response_text(response):
        try:
            return str(((response.get("choices") or [{}])[0].get("message") or {}).get("content") or "").strip()
        except (AttributeError, IndexError):
            return ""

    def _append_assistant(self, response, calls, request_ids):
        message = ((response.get("choices") or [{}])[0].get("message") or {}) if isinstance(response, dict) else {}
        native = message.get("tool_calls") or []
        if calls:
            tool_calls = []
            for i, call in enumerate(calls):
                source = native[i] if i < len(native) else {}
                fn = source.get("function") or {}
                tool_calls.append({
                    "id": request_ids[i],
                    "type": "function",
                    "function": {
                        "name": fn.get("name") or call["tool"],
                        "arguments": fn.get("arguments") or json.dumps(call["arguments"], ensure_ascii=False),
                    },
                })
            self.messages.append({
                "role": "assistant",
                "content": message.get("content") or "",
                "tool_calls": tool_calls,
            })

    def emit(self, event):
        if self.on_event:
            try:
                self.on_event(event)
            except Exception:
                self.log.exception("event callback failed")

    # -- turn lifecycle ------------------------------------------------------

    async def request(self, user, text):
        if self._turn_active:
            self.log.warning("turn already active; ignoring request")
            return
        self._begin_turn(user)
        self.messages.append({"role": "user", "content": text})
        self._compress()
        self.log.info("turn_start user=%s text=%s", user, text)
        self.emit({"kind": "user", "user": user, "text": text})
        await self._next_step(user)

    def _begin_turn(self, user):
        self._turn_active = True
        self._turn_user = user
        self._turn_iterations = 0
        self._turn_consolidation_failures = 0
        self._pending.clear()

    def _end_turn(self):
        self._turn_active = False
        self._pending.clear()
        self._save_session()

    async def _next_step(self, user):
        if not self._turn_active:
            return
        self._turn_iterations += 1
        if self._turn_iterations > _MAX_ITERATIONS:
            self.log.warning("turn_iteration_limit user=%s", user)
            await self.send({"type": "agent_error", "user": user, "error": "agent iteration limit reached"})
            self._end_turn()
            return

        try:
            response = self.llm.complete(self.messages, self.all_tools)
        except Exception as exc:
            self.log.exception("llm call failed")
            await self.send({"type": "agent_error", "user": user, "error": str(exc)})
            self._end_turn()
            return

        calls = [normalize_tool_call(c) for c in parse_calls(response)]
        if not calls:
            text = self._response_text(response)
            if text:
                self.messages.append({"role": "assistant", "content": text})
                self.emit({"kind": "agent_message", "user": user, "text": text})
                await self.send({"type": "agent_message", "user": user, "message": text})
            else:
                await self.send({"type": "agent_error", "user": user, "error": "LLM returned no valid tool call", "raw": response})
            self._end_turn()
            return

        request_ids = [str(uuid.uuid4()) for _ in calls]
        self._append_assistant(response, calls, request_ids)
        issued_remote = False
        for call, request_id in zip(calls, request_ids):
            tool = call.get("tool")
            args = call.get("arguments") or {}

            if tool == "memory":
                result = self._run_memory(args)
                self.messages.append({"role": "tool", "tool_call_id": request_id,
                                      "content": json.dumps(result, ensure_ascii=False)})
                continue  # local tool: keep looping

            terminal = tool == "chat_public"
            self._pending[request_id] = {
                "user": user, "tool": tool, "arguments": args,
                "terminal": terminal, "retries": 0,
            }
            issued_remote = True
            self.log.info("tool_sent id=%s user=%s tool=%s", request_id, user, tool)
            self.emit({"kind": "tool_call", "id": request_id, "user": user, "tool": tool, "arguments": args})
            await self.send({"type": "tool_call", "id": request_id, "user": user, "tool": tool, "arguments": args})

        if issued_remote:
            return  # wait for result() callbacks to continue the turn
        await self._next_step(user)  # memory-only step: continue synchronously

    def _run_memory(self, args):
        result = handle_memory_tool_call(self.memory_store, args)
        # Hermes consolidation-failure cap: after N failed consolidation
        # attempts in one turn, stop asking the model to retry.
        if not result.get("success") and "would exceed" in result.get("error", "") or            not result.get("success") and "no entry matched" in result.get("error", ""):
            self._turn_consolidation_failures += 1
            if self._turn_consolidation_failures > _MAX_CONSOLIDATION_FAILURES:
                result = {"success": False, "done": True,
                          "error": "Memory consolidation failed repeatedly this turn. "
                                   "Leave memory unchanged and continue replying to the owner."}
        self.emit({"kind": "memory_write", "success": result.get("success"), "target": args.get("target")})
        return result

    async def result(self, message):
        request_id = message.get("id")
        pending = self._pending.pop(request_id, None)
        if pending is None:
            return
        self.messages.append({"role": "tool", "tool_call_id": request_id,
                              "content": json.dumps(message, ensure_ascii=False)})
        status = message.get("status")
        user = pending["user"]
        self.emit({"kind": "tool_result", "id": request_id, "status": status,
                   "error": message.get("error")})

        if status == "running":
            self._pending[request_id] = pending
            return

        # Retry Java tool failures (not terminal calls)
        if status in {"failed", "cancelled"} and not pending["terminal"] and pending["retries"] < _MAX_RETRIES:
            pending["retries"] += 1
            new_id = str(uuid.uuid4())
            self._pending[new_id] = pending
            self.log.info("retrying_tool tool=%s attempt=%d reason=%s",
                          pending["tool"], pending["retries"], message.get("error"))
            self.emit({"kind": "retry", "tool": pending["tool"], "attempt": pending["retries"],
                       "reason": message.get("error") or status})
            await self.send({"type": "tool_call", "id": new_id, "user": user,
                             "tool": pending["tool"], "arguments": pending["arguments"]})
            return

        # Terminal (chat_public) ends the turn
        if pending["terminal"]:
            self._end_turn()
            return

        # Once every remote call of this step has reported, continue the loop
        if not self._pending:
            await self._next_step(user)

    def observe(self, observation):
        if isinstance(observation, dict):
            self.latest_observation = observation
            self.memory = observation
