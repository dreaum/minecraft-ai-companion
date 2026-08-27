package adris.altoclef.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Central registry. Tool implementations are deliberately replaceable. */
public final class AgentToolRegistry {
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public void register(AgentTool tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) throw new IllegalArgumentException("invalid tool");
        if (tools.putIfAbsent(tool.name(), tool) != null) throw new IllegalArgumentException("duplicate tool: " + tool.name());
    }

    public ToolResult call(String name, JsonNode arguments) {
        AgentTool tool = tools.get(name);
        return tool == null ? ToolResult.failed("unknown tool: " + name) : tool.execute(arguments);
    }

    public ToolResult callAndAudit(String caller, String name, JsonNode arguments, AgentAuditLog audit) {
        ToolResult result;
        try { result = call(name, arguments); }
        catch (RuntimeException exception) { result = ToolResult.failed(exception.getMessage()); }
        if (audit != null) audit.record(caller, name, arguments, result);
        return result;
    }

    public Collection<AgentTool> all() { return tools.values(); }
}
