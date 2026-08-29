import asyncio
import unittest

from agent_backend.agent_loop import AgentLoop


class FakeLLM:
    def __init__(self):
        self.calls = 0

    def complete(self, messages, tools):
        self.calls += 1
        if self.calls == 1:
            return {"choices": [{"message": {"tool_calls": [{"function": {"name": "altoclef_task", "arguments": '{"command":"collect dirt 1"}'}}]}}]}
        return {"choices": [{"message": {"content": "I will try a safer approach."}}]}


class AgentLoopTests(unittest.TestCase):
    def test_running_is_kept_and_failure_requests_followup(self):
        sent, events = [], []
        async def send(message): sent.append(message)
        loop = AgentLoop(FakeLLM(), send, [], on_event=events.append)
        asyncio.run(loop.request("owner", "collect dirt 1"))
        request_id = next(m["id"] for m in sent if m["type"] == "tool_call")
        asyncio.run(loop.result({"type": "tool_result", "id": request_id, "status": "running"}))
        self.assertIn(request_id, loop.pending)
        asyncio.run(loop.result({"type": "tool_result", "id": request_id, "status": "failed", "error": "unreachable"}))
        self.assertEqual(loop.followup_turns, 1)
        self.assertTrue(any(e["kind"] == "retry" for e in events))


if __name__ == "__main__":
    unittest.main()
