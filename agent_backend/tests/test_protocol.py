import unittest
from agent_backend.protocol import decode, encode
from agent_backend.llm_client import parse_calls
from agent_backend.agent_loop import direct_companion_call, normalize_tool_call

class ProtocolTests(unittest.TestCase):
    def test_round_trip_and_version(self):
        value = decode(encode({"type": "tool_result", "id": "x", "status": "completed", "ok": True}))
        self.assertEqual(value["protocol_version"], 1)

    def test_agent_message_is_valid_bridge_output(self):
        value = decode(encode({"type": "agent_message", "user": "owner", "message": "hello"}))
        self.assertEqual(value["message"], "hello")

    def test_rejects_wrong_version(self):
        with self.assertRaises(ValueError): decode('{"protocol_version":99}')

    def test_parses_plain_content_json(self):
        response = {"choices": [{"message": {"content": "prefix {\"tool\":\"move\",\"arguments\":{\"direction\":\"forward\",\"action\":\"press\"}} suffix"}}]}
        self.assertEqual(parse_calls(response)[0]["tool"], "move")

    def test_rejects_non_object_arguments(self):
        response = {"choices": [{"message": {"tool_calls": [{"function": {"name": "move", "arguments": "[]"}}]}}]}
        self.assertEqual(parse_calls(response), [])

    def test_direct_player_commands_use_companion_task(self):
        self.assertEqual(direct_companion_call("过来"), {"tool": "altoclef_task", "arguments": {"command": "come"}})
        self.assertEqual(direct_companion_call("collect oak_log 1")["arguments"]["command"], "collect oak_log 1")
        self.assertEqual(direct_companion_call("橡木原木")["arguments"]["command"], "collect oak_log 1")

    def test_free_text_remains_for_llm(self):
        self.assertIsNone(direct_companion_call("帮我在附近找个安全的地方"))

    def test_model_command_is_normalized_before_bridge(self):
        call = normalize_tool_call({"tool": "altoclef_task", "arguments": {"command": "collect oak_log 1"}})
        self.assertEqual(call["arguments"]["command"], "collect oak_log 1")

if __name__ == "__main__": unittest.main()
