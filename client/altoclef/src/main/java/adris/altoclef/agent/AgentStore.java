package adris.altoclef.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Filesystem home for Hermes-style memory, sessions, experiences and skills. */
public final class AgentStore {
    private final Path root;
    private final ObjectMapper json = new ObjectMapper();

    public AgentStore(Path root) {
        this.root = root;
        try {
            for (String dir : new String[]{"memory", "sessions", "experiences", "skills", "candidates", "archive", "tutorials"})
                Files.createDirectories(root.resolve(dir));
            seedTutorials();
        } catch (IOException e) { throw new IllegalStateException("cannot initialize agent store", e); }
    }

    private void seedTutorials() throws IOException {
        String[] bundled = {"basics/movement.md", "basics/first-night.md", "resources/wood.md", "resources/stone.md", "resources/iron.md", "crafting/tools.md", "crafting/furnace.md", "survival/water.md", "survival/lava.md", "survival/combat.md", "exploration/villages.md"};
        for (String relative : bundled) {
            Path target = root.resolve("tutorials").resolve(relative);
            if (Files.exists(target)) continue;
            try (InputStream in = AgentStore.class.getResourceAsStream("/agent/tutorials/" + relative)) {
                if (in == null) continue;
                Files.createDirectories(target.getParent());
                Files.copy(in, target);
            }
        }
    }

    public Path root() { return root; }
    public Path resolve(String relative) { return root.resolve(relative).normalize(); }
    public ObjectMapper json() { return json; }

    public void appendSession(String sessionId, ObjectNode event) {
        try { Files.writeString(root.resolve("sessions").resolve("session-" + sessionId + ".jsonl"), json.writeValueAsString(event) + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); }
        catch (IOException e) { throw new IllegalStateException("cannot persist session", e); }
    }
}
