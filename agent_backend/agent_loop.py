import json
import logging
import uuid
import re
from .llm_client import parse_calls

SYSTEM = """You control Minecraft through registered tools. Return exactly one JSON object and nothing else when native tool calls are unavailable. Never output prose, XML, Markdown fences, </tool_call>, JavaScript, or unknown fields.

Prefer altoclef_task for any player-relative movement or resource task. It is the only tool that knows the owner's real in-game entity and can pathfind safely. Do NOT use look plus raw MOVE_FORWARD to satisfy 'come here', 'come', '过来', '跟着我', or 'follow'. Examples:
user: 过来
assistant: {\"tool\":\"altoclef_task\",\"arguments\":{\"command\":\"come\"}}
user: follow me
assistant: {\"tool\":\"altoclef_task\",\"arguments\":{\"command\":\"follow\"}}
user: get one oak log
assistant: {\"tool\":\"altoclef_task\",\"arguments\":{\"command\":\"collect oak_log 1\"}}

Use canonical English Minecraft IDs such as oak_log. Use baritone_goal only when exact x, y, z coordinates are supplied. Raw key and look tools are for short, explicit physical interactions after observing the world. Observe before an unfamiliar action. After every tool result decide whether to continue, verify, or report failure."""

_DIRECT_COMMANDS = {
    "come": "come", "come here": "come", "过来": "come", "来我这里": "come",
    "follow": "follow", "follow me": "follow", "跟随": "follow", "跟着我": "follow",
    "protect": "protect", "保护我": "protect", "stop": "stop", "停止": "stop",
    "status": "status", "状态": "status", "queue": "queue", "队列": "queue",
}

# AltoClef's catalogue uses wood type names ("oak", "birch", ...), while
# the public Agent interface deliberately uses Minecraft item IDs.
_CATALOG_ALIASES = {
    "acacia_log": "acacia", "birch_log": "birch", "dark_oak_log": "dark_oak",
    "jungle_log": "jungle", "mangrove_log": "mangrove", "oak_log": "oak",
    "spruce_log": "spruce", "crimson_stem": "crimson", "warped_stem": "warped",
}


def direct_companion_call(text):
    """Map only unambiguous companion requests to the existing Java task surface."""
    normalized = " ".join(text.strip().lower().split())
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

class AgentLoop:
    def __init__(self, llm, send, tools):
        self.llm, self.send, self.tools = llm, send, tools
        self.messages = [{"role": "system", "content": SYSTEM}]
        self.pending = {}
        self.log = logging.getLogger("agent")

    async def request(self, user, text):
        self.messages.append({"role":"user", "content": text})
        try:
            direct = direct_companion_call(text)
            if direct:
                self.log.info("direct_companion_command user=%s command=%s", user, direct["arguments"]["command"])
                calls = [direct]
                response = {"choices": [{"message": {"content": "", "tool_calls": []}}]}
            else:
                response = self.llm.complete(self.messages, self.tools)
                self.log.info("llm_response=%s", json.dumps(response, ensure_ascii=False))
                calls = [normalize_tool_call(call) for call in parse_calls(response)]
            if not calls:
                text = self._response_text(response)
                await self.send({"type":"agent_message", "user": user, "message": text}) if text else await self.send({"type":"agent_error", "user":user, "error":"LLM returned no valid tool call", "raw":response})
                return
            request_ids = [str(uuid.uuid4()) for _ in calls]
            self._append_assistant_tool_calls(response, calls, request_ids)
            for request_id, call in zip(request_ids, calls):
                self.pending[request_id] = (user, direct is not None)
                self.log.info("tool_call_sent id=%s user=%s tool=%s", request_id, user, call["tool"])
                await self.send({"type":"tool_call", "id":request_id, "user":user, "tool":call["tool"], "arguments":call["arguments"]})
        except Exception as exc:
            self.log.exception("agent request failed")
            await self.send({"type":"agent_error", "user":user, "error":str(exc)})

    async def result(self, message):
        request_id = message.get("id")
        pending = self.pending.pop(request_id, None)
        if pending is None:
            self.log.warning("ignored_tool_result id=%s", request_id)
            return
        user, is_direct = pending
        self.log.info("tool_result_received id=%s status=%s", request_id, message.get("status"))
        self.messages.append({"role":"tool", "tool_call_id":request_id, "content":json.dumps(message, ensure_ascii=False)})
        if is_direct:
            # AltoClef now owns the queued companion task. Do not let a follow-up
            # model turn replace it with speculative raw-key movement.
            return
        if message.get("status") == "running": return
        try:
            response = self.llm.complete(self.messages, self.tools)
            self.log.info("llm_response=%s", json.dumps(response, ensure_ascii=False))
            calls = [normalize_tool_call(call) for call in parse_calls(response)]
            if not calls:
                text = self._response_text(response)
                await self.send({"type":"agent_message", "user": user, "message": text}) if text else await self.send({"type":"agent_error", "user":user, "error":"LLM returned no valid tool call", "raw":response})
                return
            next_ids = [str(uuid.uuid4()) for _ in calls]
            self._append_assistant_tool_calls(response, calls, next_ids)
            for next_id, call in zip(next_ids, calls):
                self.pending[next_id] = (user, False)
                self.log.info("tool_call_sent id=%s user=%s tool=%s", next_id, user, call["tool"])
                await self.send({"type":"tool_call", "id":next_id, "user":user, "tool":call["tool"], "arguments":call["arguments"]})

        except Exception as exc:
            await self.send({"type":"agent_error", "user":user, "error":str(exc)})

    @staticmethod
    def _response_text(response):
        try:
            return str(((response.get("choices") or [{}])[0].get("message") or {}).get("content") or "").strip()
        except AttributeError:
            return ""
    def _append_assistant_tool_calls(self, response, calls, request_ids):
        """Keep OpenAI-compatible assistant/tool message pairing valid."""
        message = ((response.get("choices") or [{}])[0].get("message") or {}) if isinstance(response, dict) else {}
        native = message.get("tool_calls") or []
        if calls:
            tool_calls = []
            for i, call in enumerate(calls):
                source = native[i] if i < len(native) else {}
                function = source.get("function") or {}
                tool_calls.append({
                    "id": request_ids[i],
                    "type": "function",
                    "function": {
                        "name": function.get("name") or call["tool"],
                        "arguments": function.get("arguments") or json.dumps(call["arguments"], ensure_ascii=False),
                    },
                })
            self.messages.append({
                "role": "assistant",
                "content": message.get("content") or "",
                "tool_calls": tool_calls,
            })
