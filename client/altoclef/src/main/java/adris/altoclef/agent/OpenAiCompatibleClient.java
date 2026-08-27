package adris.altoclef.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Minimal OpenAI-compatible chat-completions client. */
public final class OpenAiCompatibleClient {
    private final HttpClient http;
    private final ObjectMapper json;
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final Duration timeout;

    public OpenAiCompatibleClient(String baseUrl, String model, String apiKey, Duration timeout) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // Accept either an OpenAI server root or a base URL that already ends in /v1.
        this.endpoint = URI.create(normalized.endsWith("/v1")
                ? normalized + "/chat/completions"
                : normalized + "/v1/chat/completions");
        this.model = model;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        this.json = new ObjectMapper();
    }

    public JsonNode complete(List<JsonNode> messages, List<AgentTool> tools, boolean stream) throws IOException, InterruptedException {
        ObjectNode request = json.createObjectNode();
        request.put("model", model);
        request.put("stream", stream);
        ArrayNode messageArray = request.putArray("messages");
        messages.forEach(messageArray::add);
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolArray = request.putArray("tools");
            for (AgentTool tool : tools) {
                ObjectNode wrapper = toolArray.addObject();
                wrapper.put("type", "function");
                ObjectNode fn = wrapper.putObject("function");
                fn.put("name", tool.name());
                fn.set("parameters", tool.schema());
            }
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request)));
        if (!apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey);
        HttpRequest request = builder.build();
        IOException lastFailure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) return json.readTree(response.body());
                String body = response.body() == null ? "" : response.body();
                if (response.statusCode() != 429 && response.statusCode() < 500)
                    throw new IOException("LLM HTTP " + response.statusCode() + ": " + body);
                lastFailure = new IOException("LLM HTTP " + response.statusCode() + ": " + body);
            } catch (IOException exception) {
                lastFailure = exception;
                if (exception.getMessage() != null && exception.getMessage().startsWith("LLM HTTP ")
                        && !exception.getMessage().matches("LLM HTTP (429|5\\d\\d):.*")) throw exception;
            }
            if (attempt < 2) {
                try { Thread.sleep(500L * (attempt + 1)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("LLM request interrupted", interrupted); }
            }
        }
        throw lastFailure == null ? new IOException("LLM request failed") : lastFailure;
    }
}
