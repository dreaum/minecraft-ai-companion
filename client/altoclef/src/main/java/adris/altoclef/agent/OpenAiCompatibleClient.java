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
        this.endpoint = URI.create(normalized + "/v1/chat/completions");
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
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("LLM HTTP " + response.statusCode() + ": " + response.body());
        return json.readTree(response.body());
    }
}
