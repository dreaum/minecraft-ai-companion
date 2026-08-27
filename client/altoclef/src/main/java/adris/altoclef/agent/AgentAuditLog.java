package adris.altoclef.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Append-only JSONL audit trail for tool calls and their results. */
public final class AgentAuditLog {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path path;

    public AgentAuditLog(Path agentDir) {
        path = agentDir.resolve("audit.jsonl");
        try { Files.createDirectories(agentDir); } catch (IOException e) { throw new IllegalStateException("cannot create agent directory", e); }
    }

    public synchronized void record(String caller, String tool, JsonNode arguments, ToolResult result) {
        ObjectNode row = JSON.createObjectNode();
        row.put("timestamp", Instant.now().toString());
        row.put("caller", caller == null ? "unknown" : caller);
        row.put("tool", tool);
        row.set("arguments", arguments == null ? JSON.createObjectNode() : arguments);
        row.set("result", result.toJson());
        try {
            Files.writeString(path, JSON.writeValueAsString(row) + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) { throw new IllegalStateException("cannot write agent audit log", e); }
    }
}
