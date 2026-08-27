package adris.altoclef.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record ToolResult(boolean ok, String status, JsonNode observation, String error) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static ToolResult completed(JsonNode observation) { return new ToolResult(true, "completed", observation, null); }
    public static ToolResult running(JsonNode observation) { return new ToolResult(true, "running", observation, null); }
    public static ToolResult failed(String error) { return new ToolResult(false, "failed", JSON.createObjectNode(), error); }
    public static ToolResult cancelled(String reason) { return new ToolResult(false, "cancelled", JSON.createObjectNode(), reason); }

    public ObjectNode toJson() {
        ObjectNode out = JSON.createObjectNode();
        out.put("ok", ok);
        out.put("status", status);
        out.set("observation", observation == null ? JSON.createObjectNode() : observation);
        if (error == null) out.putNull("error"); else out.put("error", error);
        return out;
    }
}
