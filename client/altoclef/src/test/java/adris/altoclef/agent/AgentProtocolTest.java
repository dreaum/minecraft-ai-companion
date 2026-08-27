package adris.altoclef.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentProtocolTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void parsesOnlyStructuredToolCalls() throws Exception {
        var response = json.readTree("{\"choices\":[{\"message\":{\"content\":\"ignore\",\"tool_calls\":[{\"function\":{\"name\":\"observe_world\",\"arguments\":\"{\\\"detail\\\":true}\"}}]}}]}");
        List<AgentCall> calls = AgentResponseParser.toolCalls(response);
        assertEquals(1, calls.size());
        assertEquals("observe_world", calls.get(0).tool());
        assertTrue(calls.get(0).arguments().path("detail").asBoolean());
    }

    @Test
    void malformedArgumentsAreRejected() throws Exception {
        var response = json.readTree("{\"choices\":[{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"look\",\"arguments\":\"not-json\"}}]}}]}");
        assertTrue(AgentResponseParser.toolCalls(response).isEmpty());
    }

    @Test
    void parsesJsonToolCallFromNormalMessageContent() throws Exception {
        var response = json.readTree("{\"choices\":[{\"message\":{\"content\":\"{\\\"tool\\\":\\\"observe_world\\\",\\\"arguments\\\":{}}\"}}]}");
        var calls = AgentResponseParser.toolCalls(response);
        assertEquals(1, calls.size());
        assertEquals("observe_world", calls.get(0).tool());
    }

    @Test
    void resultHasStableWireShape() {
        var result = ToolResult.failed("bad target").toJson();
        assertFalse(result.path("ok").asBoolean());
        assertEquals("failed", result.path("status").asText());
        assertEquals("bad target", result.path("error").asText());
        assertTrue(result.path("observation").isObject());
    }
}
