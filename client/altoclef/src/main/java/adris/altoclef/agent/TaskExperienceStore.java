package adris.altoclef.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/** Persists a truthful, append-only outcome for each companion task. */
public final class TaskExperienceStore {
    private final Path directory;
    private final ObjectMapper json;

    public TaskExperienceStore(AgentStore store) {
        directory = store.resolve("experiences");
        json = store.json();
    }

    public Path record(String intent, String status, String failureReason, boolean recoverable) {
        ObjectNode value = json.createObjectNode();
        value.put("id", UUID.randomUUID().toString());
        value.put("timestamp", Instant.now().toString());
        value.put("intent", intent == null ? "unknown" : intent);
        value.put("status", status);
        value.put("recoverable", recoverable);
        if (failureReason == null) value.putNull("failure_reason"); else value.put("failure_reason", failureReason);
        Path file = directory.resolve("task-" + value.get("id").asText() + ".json");
        try { Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(value)); }
        catch (IOException e) { throw new IllegalStateException("cannot persist task experience", e); }
        return file;
    }
}
