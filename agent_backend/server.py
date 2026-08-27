import asyncio
import json
import logging
import os
from pathlib import Path
from .agent_loop import AgentLoop
from .llm_client import LLMClient
from .protocol import decode, encode, PROTOCOL_VERSION

try:
    import websockets
except ImportError as exc:
    raise SystemExit("Install backend dependency with: python -m pip install websockets") from exc

ROOT = Path(__file__).resolve().parent.parent
CONFIG = Path(os.environ.get("MINECRAFT_AGENT_CONFIG", ROOT / "agent" / "llm.properties"))

def properties(path):
    out = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1); out[key.strip()] = value.strip()
    return out

async def run():
    config = properties(CONFIG)
    expected_token = config.get("token", "")
    llm = LLMClient(config.get("url", "http://127.0.0.1:11434/v1"), config.get("model", "llama3.1"), config.get("key", ""))
    logging.basicConfig(filename=str(CONFIG.parent / "python-agent.log"), level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    state = {"connected": False, "protocol_version": PROTOCOL_VERSION, "tools": []}
    async def handler(websocket):
        state["connected"] = True
        async def send(message): await websocket.send(encode(message))
        loop = AgentLoop(llm, send, state["tools"])
        try:
            async for raw in websocket:
                try: message = decode(raw)
                except Exception as exc: await send({"type":"agent_error", "error":str(exc)}); continue
                if expected_token and message.get("token") != expected_token:
                    await send({"type":"agent_error", "error":"invalid bridge token"})
                    continue
                if message.get("type") == "hello": state["tools"] = message.get("tools") or []; loop.tools = state["tools"]; await send({"type":"hello_ack"})
                elif message.get("type") == "user_request": await loop.request(message.get("user", "owner"), message.get("request", ""))
                elif message.get("type") == "tool_result": await loop.result(message)
        finally: state["connected"] = False
    async def health(reader, writer):
        try:
            await reader.read(4096)
            payload = json.dumps({"ok": True, **state}).encode()
            writer.write(b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + str(len(payload)).encode() + b"\r\nConnection: close\r\n\r\n" + payload)
            await writer.drain()
        finally:
            writer.close()
            await writer.wait_closed()
    host, port = config.get("host", "127.0.0.1"), int(config.get("port", "8765"))
    ws_server = await websockets.serve(handler, host, port)
    health_server = await asyncio.start_server(health, host, port + 1)
    try:
        await asyncio.Future()
    finally:
        ws_server.close(); await ws_server.wait_closed()
        health_server.close(); await health_server.wait_closed()

if __name__ == "__main__": asyncio.run(run())
