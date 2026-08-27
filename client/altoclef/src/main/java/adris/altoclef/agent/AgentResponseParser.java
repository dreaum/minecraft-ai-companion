package adris.altoclef.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** Parses only OpenAI function tool calls; prose or code is never executable. */
public final class AgentResponseParser {
    private static final ObjectMapper JSON = new ObjectMapper();
    private AgentResponseParser() {}

    public static List<AgentCall> toolCalls(JsonNode response) {
        List<AgentCall> result = new ArrayList<>();
        JsonNode choices = response == null ? null : response.get("choices");
        if (choices == null || !choices.isArray()) return result;
        for (JsonNode choice : choices) {
            JsonNode calls = choice.path("message").path("tool_calls");
            if (!calls.isArray()) continue;
            for (JsonNode call : calls) {
                JsonNode function = call.path("function");
                String name = function.path("name").asText("");
                String raw = function.path("arguments").asText("{}");
                try { result.add(new AgentCall(name, JSON.readTree(raw))); }
                catch (Exception ignored) { /* malformed model output is rejected */ }
            }
        }
        return result;
    }
}
