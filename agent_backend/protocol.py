import json

PROTOCOL_VERSION = 1
MESSAGE_TYPES = {"hello", "hello_ack", "user_request", "tool_call", "tool_result", "world_event", "agent_message", "agent_error", "shutdown"}
TOOL_STATUSES = {"completed", "running", "failed", "cancelled"}

def validate_message(message: dict) -> None:
    if not isinstance(message, dict):
        raise ValueError("message must be an object")
    if message.get("protocol_version", PROTOCOL_VERSION) != PROTOCOL_VERSION:
        raise ValueError(f"unsupported protocol_version: {message.get('protocol_version')}")
    kind = message.get("type")
    if kind not in MESSAGE_TYPES:
        raise ValueError(f"unsupported message type: {kind}")
    if kind in {"tool_call", "tool_result"} and not isinstance(message.get("id"), str):
        raise ValueError("tool messages require string id")
    if kind == "tool_call":
        if not isinstance(message.get("tool"), str) or not message["tool"]:
            raise ValueError("tool_call requires tool")
        if not isinstance(message.get("arguments", {}), dict):
            raise ValueError("tool_call arguments must be an object")
    if kind == "tool_result" and message.get("status") not in TOOL_STATUSES:
        raise ValueError("tool_result has invalid status")
    if kind == "world_event" and not isinstance(message.get("event"), str):
        raise ValueError("world_event requires event")

def encode(message: dict) -> str:
    value = dict(message)
    value.setdefault("protocol_version", PROTOCOL_VERSION)
    validate_message(value)
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))

def decode(raw: str) -> dict:
    value = json.loads(raw)
    validate_message(value)
    return value
