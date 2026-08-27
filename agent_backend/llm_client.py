import json
import urllib.request
import urllib.error

class LLMClient:
    def __init__(self, url: str, model: str, key: str, timeout: float = 60):
        base = url.rstrip("/")
        self.endpoint = base if base.endswith("/chat/completions") else base + "/chat/completions"
        self.model, self.key, self.timeout = model, key, timeout

    def complete(self, messages, tools):
        body = {"model": self.model, "messages": messages, "stream": False}
        if tools:
            body["tools"] = [{"type": "function", "function": {"name": t["name"], "parameters": t.get("schema", {})}} for t in tools]
        request = urllib.request.Request(self.endpoint, data=json.dumps(body, ensure_ascii=False).encode(), headers={"Content-Type":"application/json", **({"Authorization":"Bearer " + self.key} if self.key else {})})
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", "replace")
            raise RuntimeError(f"LLM HTTP {exc.code}: {detail[:1000]}") from exc

def parse_calls(response: dict):
    choices = response.get("choices") or []
    calls = []
    for choice in choices:
        message = choice.get("message") or {}
        for call in message.get("tool_calls") or []:
            fn = call.get("function") or {}
            try: args = json.loads(fn.get("arguments") or "{}")
            except json.JSONDecodeError: continue
            if fn.get("name") and isinstance(args, dict): calls.append({"tool": fn["name"], "arguments": args})
        if not calls:
            content = message.get("content") or ""
            start, end = content.find("{"), content.rfind("}")
            if start >= 0 and end > start:
                try:
                    value = json.loads(content[start:end + 1])
                    if value.get("tool") and isinstance(value.get("arguments"), dict): calls.append(value)
                except (json.JSONDecodeError, AttributeError): pass
    return calls
