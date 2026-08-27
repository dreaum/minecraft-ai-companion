package adris.altoclef.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
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
        try (PreparedStatement p = connection.prepareStatement("SELECT t.id,t.title,t.path,snippet(tutorial_fts,2,'','', '...', 24) FROM tutorial_fts f JOIN tutorials t ON t.id=f.id WHERE tutorial_fts MATCH ? LIMIT ?")) {
            p.setString(1, query); p.setInt(2, Math.max(1, Math.min(limit, 50)));
            try (ResultSet r = p.executeQuery()) { while (r.next()) hits.add(new Hit(r.getString(1), r.getString(2), r.getString(3), r.getString(4))); }
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
