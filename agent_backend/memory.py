"""Hermes-style persistent curated memory.

Ported from NousResearch/hermes-agent tools/memory_tool.py — the core design
is kept faithful: two file-backed stores with a frozen system-prompt snapshot
and a single "memory" tool exposing add/replace/remove.

Stores:
  MEMORY.md  — the companion's own notes and observations (world facts,
               player preferences, task conventions, things learned).
  USER.md    — what the companion knows about the owner (communication style,
               expectations, workflow habits).

Both files are injected into the system prompt as a FROZEN snapshot at
session start. Mid-session writes update the files on disk immediately
(durable) but do NOT change the system prompt — this keeps the prompt-prefix
cache stable for the whole session. The snapshot refreshes on next start.

Entry delimiter: "§" (section sign). Entries may be multiline.
Character limits (not tokens): memory 2200, user 1375.
"""

import json
import os
from pathlib import Path

ENTRY_DELIMITER = "\n§\n"

MEMORY_BLOCK_HEADERS = {
    "memory": "MEMORY (your personal notes)",
    "user": "USER PROFILE (who the user is)",
}

DEFAULT_MEMORY_CHAR_LIMIT = 2200
DEFAULT_USER_CHAR_LIMIT = 1375


class MemoryStore:
    """Bounded curated memory with file persistence.

    Maintains two parallel states (mirroring Hermes):
      - _system_prompt_snapshot: frozen at load time, injected into the
        system prompt. Never mutated mid-session.
      - memory_entries / user_entries: live state, mutated by tool calls and
        persisted to disk. Tool responses reflect this live state.
    """

    def __init__(
        self,
        memory_dir,
        memory_char_limit: int = DEFAULT_MEMORY_CHAR_LIMIT,
        user_char_limit: int = DEFAULT_USER_CHAR_LIMIT,
    ):
        self.memory_dir = Path(memory_dir)
        self.memory_entries = []
        self.user_entries = []
        self.memory_char_limit = memory_char_limit
        self.user_char_limit = user_char_limit
        self._system_prompt_snapshot = {"memory": "", "user": ""}

    # -- disk ---------------------------------------------------------------

    def _path_for(self, target: str) -> Path:
        if target == "user":
            return self.memory_dir / "USER.md"
        return self.memory_dir / "MEMORY.md"

    @staticmethod
    def _read_file(path: Path):
        if not path.exists():
            return []
        try:
            raw = path.read_text(encoding="utf-8").strip()
        except Exception:
            return []
        if not raw:
            return []
        return [part.strip() for part in raw.split(ENTRY_DELIMITER) if part.strip()]

    def _write_file(self, path: Path, entries):
        path.parent.mkdir(parents=True, exist_ok=True)
        body = ENTRY_DELIMITER.join(entries)
        tmp = path.with_suffix(path.suffix + ".tmp")
        tmp.write_text(body, encoding="utf-8")
        os.replace(tmp, path)

    def load_from_disk(self):
        """Load entries from MEMORY.md and USER.md and freeze the snapshot."""
        self.memory_dir.mkdir(parents=True, exist_ok=True)
        self.memory_entries = self._read_file(self._path_for("memory"))
        self.user_entries = self._read_file(self._path_for("user"))
        # Deduplicate preserving order and first occurrence.
        self.memory_entries = list(dict.fromkeys(self.memory_entries))
        self.user_entries = list(dict.fromkeys(self.user_entries))
        self._system_prompt_snapshot = {
            "memory": self._render_block("memory", self.memory_entries),
            "user": self._render_block("user", self.user_entries),
        }

    def save_to_disk(self, target: str):
        self._write_file(self._path_for(target), self._entries_for(target))

    # -- helpers ------------------------------------------------------------

    def _entries_for(self, target: str):
        if target == "user":
            return self.user_entries
        return self.memory_entries

    def _set_entries(self, target: str, entries):
        if target == "user":
            self.user_entries = entries
        else:
            self.memory_entries = entries

    def _char_count(self, target: str) -> int:
        entries = self._entries_for(target)
        if not entries:
            return 0
        return len(ENTRY_DELIMITER.join(entries))

    def _char_limit(self, target: str) -> int:
        if target == "user":
            return self.user_char_limit
        return self.memory_char_limit

    @staticmethod
    def _render_block(target: str, entries) -> str:
        header = MEMORY_BLOCK_HEADERS.get(target, "MEMORY")
        if not entries:
            return ""
        lines = [header, "=" * len(header)]
        for entry in entries:
            lines.append("  " + entry.replace("\n", "\n  "))
        return "\n".join(lines)

    def system_prompt_block(self) -> str:
        """Return the frozen snapshot for injection into the system prompt."""
        parts = []
        if self._system_prompt_snapshot.get("memory"):
            parts.append(self._system_prompt_snapshot["memory"])
        if self._system_prompt_snapshot.get("user"):
            parts.append(self._system_prompt_snapshot["user"])
        return ("\n\n".join(parts) + "\n") if parts else ""

    def live_state(self, target: str) -> dict:
        entries = self._entries_for(target)
        return {
            "target": target,
            "entries": list(entries),
            "usage": f"{self._char_count(target):,}/{self._char_limit(target):,}",
        }

    def _success_response(self, target: str, message: str) -> dict:
        result = {"success": True, "message": message}
        result.update(self.live_state(target))
        return result

    @staticmethod
    def _previews(entries, max_len=240):
        out = []
        for entry in entries:
            preview = entry if len(entry) <= max_len else entry[:max_len] + "..."
            out.append(preview)
        return out

    # -- tool operations ----------------------------------------------------

    def add(self, target: str, content: str) -> dict:
        content = (content or "").strip()
        if not content:
            return {"success": False, "error": "Content cannot be empty."}

        self.load_from_disk()  # refresh live state from disk under single-process assumption
        entries = self._entries_for(target)
        limit = self._char_limit(target)

        if content in entries:
            return self._success_response(target, "Entry already exists (no duplicate added).")

        new_entries = entries + [content]
        new_total = len(ENTRY_DELIMITER.join(new_entries))
        if new_total > limit:
            current = self._char_count(target)
            return {
                "success": False,
                "error": (
                    f"Memory at {current:,}/{limit:,} chars. Adding this entry "
                    f"({len(content)} chars) would exceed the limit. Consolidate: "
                    f"use replace to merge overlapping entries or remove stale ones, "
                    f"then retry."
                ),
                "current_entries": entries,
                "usage": f"{current:,}/{limit:,}",
            }

        entries.append(content)
        self._set_entries(target, entries)
        self.save_to_disk(target)
        return self._success_response(target, "Entry added.")

    def replace(self, target: str, old_text: str, new_content: str) -> dict:
        old_text = (old_text or "").strip()
        new_content = (new_content or "").strip()
        if not old_text:
            return {"success": False, "error": "old_text cannot be empty."}
        if not new_content:
            return {"success": False, "error": "new_content cannot be empty. Use remove to delete entries."}

        self.load_from_disk()
        entries = self._entries_for(target)
        matches = [(i, e) for i, e in enumerate(entries) if old_text in e]

        if not matches:
            return {
                "success": False,
                "error": f"No entry matched '{old_text}'. Check current_entries and retry with the exact text.",
                "current_entries": entries,
            }
        if len(matches) > 1:
            unique_texts = {e for _, e in matches}
            if len(unique_texts) > 1:
                return {
                    "success": False,
                    "error": f"Multiple entries matched '{old_text}'. Be more specific.",
                    "matches": self._previews([e for _, e in matches]),
                }

        idx = matches[0][0]
        limit = self._char_limit(target)
        test_entries = entries.copy()
        test_entries[idx] = new_content
        new_total = len(ENTRY_DELIMITER.join(test_entries))
        if new_total > limit:
            current = self._char_count(target)
            return {
                "success": False,
                "error": (
                    f"Replacement would put memory at {new_total:,}/{limit:,} chars. "
                    f"Shorten the new content or remove other entries first."
                ),
                "current_entries": entries,
                "usage": f"{current:,}/{limit:,}",
            }

        entries[idx] = new_content
        self._set_entries(target, entries)
        self.save_to_disk(target)
        return self._success_response(target, "Entry replaced.")

    def remove(self, target: str, old_text: str) -> dict:
        old_text = (old_text or "").strip()
        if not old_text:
            return {"success": False, "error": "old_text cannot be empty."}

        self.load_from_disk()
        entries = self._entries_for(target)
        matches = [(i, e) for i, e in enumerate(entries) if old_text in e]

        if not matches:
            return {
                "success": False,
                "error": f"No entry matched '{old_text}'. Check current_entries and retry with the exact text.",
                "current_entries": entries,
            }
        if len(matches) > 1:
            unique_texts = {e for _, e in matches}
            if len(unique_texts) > 1:
                return {
                    "success": False,
                    "error": f"Multiple entries matched '{old_text}'. Be more specific.",
                    "matches": self._previews([e for _, e in matches]),
                }

        idx = matches[0][0]
        entries.pop(idx)
        self._set_entries(target, entries)
        self.save_to_disk(target)
        return self._success_response(target, "Entry removed.")


MEMORY_TOOL_SCHEMA = {
    "name": "memory",
    "description": (
        "Persistent notes. Use to remember facts about this Minecraft world, the "
        "owner, tasks, and conventions across sessions. Two separate stores: "
        "'memory' for your own notes and observations, 'user' for what you know "
        "about the owner. Entries are injected back into your context next session; "
        "writes are durable immediately. Replace or remove entries with a short "
        "unique substring."
    ),
    "parameters": {
        "type": "object",
        "properties": {
            "action": {
                "type": "string",
                "enum": ["add", "replace", "remove"],
                "description": "add a new entry, replace an existing entry, or remove an existing entry.",
            },
            "target": {
                "type": "string",
                "enum": ["memory", "user"],
                "description": "memory = your own notes; user = facts about the owner.",
            },
            "content": {
                "type": "string",
                "description": "For add: the full new entry. For replace: the replacement text.",
            },
            "old_text": {
                "type": "string",
                "description": "For replace/remove: a short unique substring of the existing entry.",
            },
        },
        "required": ["action", "target"],
    },
}


def handle_memory_tool_call(store: MemoryStore, arguments: dict) -> dict:
    """Execute one memory tool call and return a JSON-serializable result."""
    action = (arguments or {}).get("action", "")
    target = (arguments or {}).get("target", "")
    if target not in {"memory", "user"}:
        return {"success": False, "error": "target must be 'memory' or 'user'."}
    if action == "add":
        return store.add(target, arguments.get("content", ""))
    if action == "replace":
        return store.replace(target, arguments.get("old_text", ""), arguments.get("content", ""))
    if action == "remove":
        return store.remove(target, arguments.get("old_text", ""))
    return {"success": False, "error": "action must be 'add', 'replace', or 'remove'."}


def memory_tool_definition():
    """Return the memory tool in this project's tool format (name + schema)."""
    return {
        "name": "memory",
        "description": MEMORY_TOOL_SCHEMA["description"],
        "schema": {
            "type": "object",
            "properties": MEMORY_TOOL_SCHEMA["parameters"]["properties"],
            "required": MEMORY_TOOL_SCHEMA["parameters"]["required"],
        },
    }

