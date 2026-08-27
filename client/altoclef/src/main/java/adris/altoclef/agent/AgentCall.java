package adris.altoclef.agent;

import com.fasterxml.jackson.databind.JsonNode;

/** Strictly structured tool call parsed from an LLM response. */
public record AgentCall(String tool, JsonNode arguments) {
    public AgentCall {
        if (tool == null || tool.isBlank()) throw new IllegalArgumentException("tool name is required");
        if (arguments == null || !arguments.isObject()) throw new IllegalArgumentException("tool arguments must be an object");
    }
}
