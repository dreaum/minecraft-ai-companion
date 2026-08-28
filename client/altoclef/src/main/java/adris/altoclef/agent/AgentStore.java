package adris.altoclef.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Filesystem home for the local agent bridge: configuration, sessions and audit trail. */
public final class AgentStore {
    private final Path root;
    private final ObjectMapper json = new ObjectMapper();

    public AgentStore(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("cannot initialize agent store", e);
        }
    }

    public Path root() { return root; }
    public Path resolve(String relative) { return root.resolve(relative).normalize(); }
    public ObjectMapper json() { return json; }
}
