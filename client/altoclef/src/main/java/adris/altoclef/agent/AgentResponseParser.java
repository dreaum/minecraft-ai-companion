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
            if (calls.isArray()) {
                for (JsonNode call : calls) {
                    JsonNode function = call.path("function");
                    String name = function.path("name").asText("");
                    String raw = function.path("arguments").asText("{}");
                    try { result.add(new AgentCall(name, JSON.readTree(raw))); }
                    catch (Exception ignored) { /* malformed model output is rejected */ }
                }
            }
            if (result.isEmpty()) {
                String content = choice.path("message").path("content").asText("").trim();
                parseJsonContent(content, result);
            }
        }
        return result;
    }

    private static void parseJsonContent(String content, List<AgentCall> result) {
        if (content.isBlank()) return;
        String candidate = content;
        if (candidate.startsWith("```") && candidate.endsWith("```")) {
            int newline = candidate.indexOf('\n');
            candidate = newline >= 0 ? candidate.substring(newline + 1, candidate.length() - 3).trim() : "";
        }
        try {
            JsonNode node = JSON.readTree(candidate);
            if (node != null && node.isArray()) {
                for (JsonNode call : node) addJsonCall(call, result);
            } else {
                addJsonCall(node, result);
            }
        } catch (Exception ignored) {
            // Models sometimes add a short sentence around the JSON. Extract the
            // outermost object and still validate it as structured JSON.
            int start = candidate.indexOf('{');
            int end = candidate.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try { addJsonCall(JSON.readTree(candidate.substring(start, end + 1)), result); }
                catch (Exception ignoredAgain) { /* reject malformed output */ }
            }
        }
    }

    private static void addJsonCall(JsonNode node, List<AgentCall> result) {
        if (node == null || !node.isObject()) return;
        String name = node.path("tool").asText(node.path("name").asText(""));
        JsonNode args = node.has("arguments") ? node.get("arguments") : node.path("args");
        if (!name.isBlank() && args != null && args.isObject()) result.add(new AgentCall(name, args));
    }
}
