package adris.altoclef.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Local Markdown catalog with SQLite FTS indexing when the JDBC driver is present. */
public final class TutorialIndex implements AutoCloseable {
    public record Hit(String id, String title, String path, String snippet) {}

    private final Connection connection;
    private final Path root;

    public TutorialIndex(AgentStore store) throws SQLException {
        root = store.resolve("tutorials");
        connection = DriverManager.getConnection("jdbc:sqlite:" + store.resolve("tutorials.db"));
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS tutorials (id TEXT PRIMARY KEY, title TEXT NOT NULL, path TEXT NOT NULL, content TEXT NOT NULL)");
            s.executeUpdate("CREATE VIRTUAL TABLE IF NOT EXISTS tutorial_fts USING fts5(id UNINDEXED, title, content)");
        }
    }

    public int rebuild() throws IOException, SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("DELETE FROM tutorials");
            s.executeUpdate("DELETE FROM tutorial_fts");
        }
        int count = 0;
        if (!Files.exists(root)) return 0;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".md")).toList()) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                String id = root.relativize(path).toString().replace('\\', '/');
                String title = content.lines().filter(line -> line.startsWith("# ")).findFirst().orElse(id).substring(2).trim();
                try (PreparedStatement p = connection.prepareStatement("INSERT INTO tutorials(id,title,path,content) VALUES(?,?,?,?)")) {
                    p.setString(1, id); p.setString(2, title); p.setString(3, root.relativize(path).toString()); p.setString(4, content); p.executeUpdate();
                }
                try (PreparedStatement p = connection.prepareStatement("INSERT INTO tutorial_fts(id,title,content) VALUES(?,?,?)")) {
                    p.setString(1, id); p.setString(2, title); p.setString(3, content); p.executeUpdate();
                }
                count++;
            }
        }
        return count;
    }

    public List<Hit> search(String query, int limit) throws SQLException {
        List<Hit> hits = new ArrayList<>();
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        try (PreparedStatement p = connection.prepareStatement("SELECT t.id,t.title,t.path,snippet(tutorial_fts,2,'','', '...', 24) FROM tutorial_fts f JOIN tutorials t ON t.id=f.id WHERE tutorial_fts MATCH ? LIMIT ?")) {
            p.setString(1, query); p.setInt(2, boundedLimit);
            try (ResultSet r = p.executeQuery()) { while (r.next()) hits.add(new Hit(r.getString(1), r.getString(2), r.getString(3), r.getString(4))); }
        } catch (SQLException ignored) {
            // FTS MATCH rejects punctuation and some natural-language input.
        }
        // The default FTS tokenizer does not reliably segment Chinese text. Keep
        // keyword search useful for Chinese requests and item names by falling
        // back to a literal, parameterized substring query.
        if (hits.isEmpty() && query != null && !query.isBlank()) {
            String normalized = query.trim();
            List<String> terms = new ArrayList<>();
            terms.add(normalized);
            // Chinese requests often contain several concepts without spaces.
            // Two-character n-grams provide useful fallback keywords while the
            // parameterized query below keeps the input safe.
            if (normalized.codePoints().allMatch(c -> c > 0x7f) && normalized.length() <= 32) {
                // The final words usually name the requested item (for example
                // "获取橡木原木" ends with "原木"), so search them before broad
                // leading verbs such as "获取".
                for (int i = normalized.length() - 2; i >= 0; i--) terms.add(normalized.substring(i, i + 2));
            }
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (String term : terms) {
                if (hits.size() >= boundedLimit) break;
                try (PreparedStatement p = connection.prepareStatement("SELECT id,title,path,content FROM tutorials WHERE lower(content) LIKE lower(?) OR lower(title) LIKE lower(?)")) {
                    String pattern = "%" + term + "%";
                    p.setString(1, pattern); p.setString(2, pattern);
                    try (ResultSet r = p.executeQuery()) {
                        while (r.next() && hits.size() < boundedLimit) {
                            if (!seen.add(r.getString(1))) continue;
                            String content = r.getString(4).replaceAll("\\s+", " ");
                            hits.add(new Hit(r.getString(1), r.getString(2), r.getString(3), content.substring(0, Math.min(content.length(), 240))));
                        }
                    }
                }
            }
        }
        return hits;
    }

    public String read(String id) throws IOException {
        Path file = root.resolve(id).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IOException("tutorial not found: " + id);
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Override public void close() throws SQLException { connection.close(); }
}
