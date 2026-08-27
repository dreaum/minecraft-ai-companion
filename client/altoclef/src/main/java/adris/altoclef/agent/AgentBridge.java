package adris.altoclef.agent;

import adris.altoclef.AltoClef;
import adris.altoclef.ui.MessagePriority;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Local WebSocket bridge. Network callbacks never touch Minecraft objects. */
public final class AgentBridge implements WebSocket.Listener {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AltoClef mod;
    private final AgentToolRegistry tools;
    private final AgentAuditLog audit;
    private final Queue<JsonNode> inbound = new ArrayDeque<>();
    private WebSocket socket;
    private Process backend;
    private String partial = "";
    private String token = "";
    private boolean connected;
    private long tickCounter;

    public AgentBridge(AltoClef mod, AgentToolRegistry tools, AgentAuditLog audit) {
        this.mod = mod; this.tools = tools; this.audit = audit;
        Path root = mod.getAgentStore().root();
        try { Files.createDirectories(root); } catch (Exception ignored) {}
        Properties p = new Properties();
        Path config = root.resolve("bridge.properties");
        if (Files.exists(config)) try (var reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) { p.load(reader); } catch (Exception e) { mod.log("Agent bridge config: " + e.getMessage()); }
        token = p.getProperty("token", "");
        String ws = p.getProperty("websocket", "ws://127.0.0.1:8765");
        startBackend(p, root);
        for (int attempt = 0; attempt < 8 && !connected; attempt++) {
            try {
                socket = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
                        .newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(URI.create(ws), this).join();
            } catch (Exception e) {
                if (attempt == 7) mod.log("Agent backend unavailable: " + rootCause(e));
                else try { Thread.sleep(500L); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    private void startBackend(Properties p, Path root) {
        if (!Boolean.parseBoolean(p.getProperty("autostart", "true"))) return;
        String script = p.getProperty("script", root.resolve("backend").resolve("start-agent.bat").toString());
        try {
            String healthUrl = p.getProperty("health", "http://127.0.0.1:" + p.getProperty("health_port", "8766"));
            HttpRequest health = HttpRequest.newBuilder(URI.create(healthUrl)).timeout(Duration.ofMillis(500)).GET().build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(health, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) return;
        } catch (Exception ignored) { /* backend is not running */ }
        try {
            Path path = Path.of(script);
            if (Files.exists(path)) {
                ProcessBuilder builder = new ProcessBuilder("cmd", "/c", "\"" + path.toString() + "\"").directory(path.getParent().toFile());
                builder.environment().put("MINECRAFT_AGENT_CONFIG", root.resolve("llm.properties").toString());
                backend = builder.start();
            }
        } catch (Exception e) { mod.log("Agent backend start failed: " + e.getMessage()); }
    }

    private static String rootCause(Throwable e) { Throwable c = e; while (c.getCause() != null) c = c.getCause(); return c.getMessage() == null ? c.toString() : c.getMessage(); }

    public boolean isConnected() { return connected && socket != null; }

    public void submitUserRequest(String user, String request) {
        ObjectNode msg = JSON.createObjectNode(); msg.put("type", "user_request"); msg.put("id", UUID.randomUUID().toString()); msg.put("user", user); msg.put("request", request); send(msg);
    }

    public void tick() {
        tickCounter++;
        if (connected && tickCounter % 20 == 0) {
            ToolResult observation = tools.callAndAudit("bridge", "observe_world", JSON.createObjectNode(), audit);
            ObjectNode event = JSON.createObjectNode(); event.put("type", "world_event"); event.put("event", "tick"); event.set("observation", observation.observation()); send(event);
        }
        JsonNode message;
        synchronized (inbound) { message = inbound.poll(); }
        if (message == null) return;
        if ("tool_call".equals(message.path("type").asText())) {
            String id = message.path("id").asText("");
            String caller = message.path("user").asText("python");
            ToolResult result = tools.callAndAudit(caller, message.path("tool").asText(""), message.path("arguments"), audit);
            ObjectNode reply = JSON.createObjectNode(); reply.put("type", "tool_result"); reply.put("id", id); reply.setAll(result.toJson()); send(reply);
        } else if ("agent_error".equals(message.path("type").asText())) {
            String user = message.path("user").asText("");
            String error = message.path("error").asText("unknown agent error");
            if (!user.isBlank()) mod.getButler().sendPublic("AI failed: " + error, MessagePriority.ASAP);
            mod.log("Agent error: " + error);
        } else if ("agent_message".equals(message.path("type").asText())) {
            String text = message.path("message").asText("").trim();
            if (!text.isBlank()) mod.getButler().sendPublic(text, MessagePriority.TIMELY);
        }
    }

    private void send(JsonNode node) {
        if (socket == null) return;
        ObjectNode out = node.deepCopy();
        out.put("protocol_version", 1);
        if (!token.isBlank()) out.put("token", token);
        socket.sendText(out.toString(), true);
    }

    public void close() {
        mod.stopTasks(); mod.getInputControls().releaseAll();
        if (socket != null) socket.sendClose(WebSocket.NORMAL_CLOSURE, "client stopping");
        if (backend != null && backend.isAlive()) backend.destroy();
    }

    @Override public void onOpen(WebSocket webSocket) {
        connected = true; socket = webSocket; webSocket.request(1);
        ObjectNode hello = JSON.createObjectNode(); hello.put("type", "hello"); hello.put("protocol_version", 1);
        ArrayNode schemas = hello.putArray("tools"); for (AgentTool tool : tools.all()) { ObjectNode t = schemas.addObject(); t.put("name", tool.name()); t.set("schema", tool.schema()); }
        send(hello);
    }
    @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        partial += data;
        if (last) { try { synchronized (inbound) { inbound.add(JSON.readTree(partial)); } } catch (Exception e) { mod.log("Agent protocol JSON error: " + e.getMessage()); } partial = ""; }
        webSocket.request(1); return null;
    }
    @Override public void onError(WebSocket webSocket, Throwable error) { connected = false; mod.stopTasks(); mod.getInputControls().releaseAll(); mod.log("Agent bridge error: " + error.getMessage()); }
    @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) { connected = false; mod.stopTasks(); mod.getInputControls().releaseAll(); return null; }
}
