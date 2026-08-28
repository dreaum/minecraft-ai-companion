package adris.altoclef.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultTest {

    @Test
    void completedHasStableWireShape() {
        var result = ToolResult.completed(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("health", 20)).toJson();
        assertTrue(result.path("ok").asBoolean());
        assertEquals("completed", result.path("status").asText());
        assertTrue(result.path("observation").path("health").canConvertToInt());
        assertEquals(20, result.path("observation").path("health").asInt());
    }

    @Test
    void failedHasStableWireShape() {
        var result = ToolResult.failed("bad target").toJson();
        assertFalse(result.path("ok").asBoolean());
        assertEquals("failed", result.path("status").asText());
        assertEquals("bad target", result.path("error").asText());
        assertTrue(result.path("observation").isObject());
    }

    @Test
    void cancelledAndRunningHaveStableStatuses() {
        assertEquals("cancelled", ToolResult.cancelled("stop").status());
        assertEquals("running", ToolResult.running(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()).status());
    }
}
