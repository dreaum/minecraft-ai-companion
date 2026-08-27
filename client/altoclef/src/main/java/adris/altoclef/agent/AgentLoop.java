package adris.altoclef.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Tick-driven orchestration: network work is async, Minecraft tools run on the client thread. */
public final class AgentLoop {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final OpenAiCompatibleClient llm;
    private final AgentToolRegistry tools;
    private final AgentAuditLog audit;
    private final Queue<PendingCall> pending = new ArrayDeque<>();
    private volatile boolean busy;

    public AgentLoop(OpenAiCompatibleClient llm, AgentToolRegistry tools, AgentAuditLog audit) {
        this.llm = llm; this.tools = tools; this.audit = audit;
    }

    public boolean isBusy() { return busy || !pending.isEmpty(); }

    public void submit(List<JsonNode> messages) { submit(messages, result -> {}); }

    public void submit(List<JsonNode> messages, Consumer<ToolResult> resultSink) {
        if (busy) return;
        busy = true;
        CompletableFuture.supplyAsync(() -> {
            try { return llm.complete(messages, List.copyOf(tools.all()), false); }
            catch (Exception e) { throw new CompletionException(e); }
        }).whenComplete((response, error) -> {
            if (error != null) { busy = false; resultSink.accept(ToolResult.failed(error.getCause() == null ? error.getMessage() : error.getCause().getMessage())); return; }
            synchronized (pending) {
                List<AgentCall> calls = AgentResponseParser.toolCalls(response);
                if (calls.isEmpty()) { busy = false; resultSink.accept(ToolResult.failed("LLM returned no tool call")); return; }
                for (AgentCall call : calls) pending.add(new PendingCall("llm", call, resultSink));
            }
        });
    }

    /** Must be called from the Fabric client tick. Executes at most one call per tick. */
    public void tick() {
        PendingCall call;
        synchronized (pending) { call = pending.poll(); }
        if (call == null) { if (busy) return; return; }
        ToolResult result = tools.callAndAudit(call.caller(), call.call().tool(), call.call().arguments(), audit);
        call.resultSink().accept(result);
        if (!result.ok() || pending.isEmpty()) busy = false;
    }

    private record PendingCall(String caller, AgentCall call, Consumer<ToolResult> resultSink) {}
}
