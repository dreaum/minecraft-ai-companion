package adris.altoclef.agent;

import com.fasterxml.jackson.databind.JsonNode;

/** A callable capability exposed to the local agent. */
public interface AgentTool {
    String name();
    JsonNode schema();
    ToolResult execute(JsonNode arguments);
}
