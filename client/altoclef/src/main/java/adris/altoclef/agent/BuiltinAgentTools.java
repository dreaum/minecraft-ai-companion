package adris.altoclef.agent;

import adris.altoclef.AltoClef;
import adris.altoclef.ui.MessagePriority;
import baritone.api.utils.input.Input;
import baritone.api.pathing.goals.GoalBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;

/** Initial tools backed by the real client. More specialised tools build on this registry. */
public final class BuiltinAgentTools {
    private static final ObjectMapper JSON = new ObjectMapper();
    private BuiltinAgentTools() {}

    public static void register(AltoClef mod, AgentToolRegistry registry) {
        registry.register(new ObserveWorldTool());
        registry.register(new StopAllTool(mod));
        registry.register(new PressKeyTool(mod));
        registry.register(new LookTool(mod));
        registry.register(new AltoClefTaskTool(mod));
        registry.register(new BaritoneGoalTool(mod));
        registry.register(new ChatPrivateTool(mod));
    }

    private static ObjectNode schema(String properties, String required) {
        ObjectNode root = JSON.createObjectNode(); root.put("type", "object");
        try {
            root.set("properties", JSON.readTree(properties));
            if (required != null) root.set("required", JSON.readTree(required));
        } catch (java.io.IOException e) { throw new IllegalArgumentException("invalid tool schema", e); }
        return root;
    }

    private static final class ObserveWorldTool implements AgentTool {
        public String name() { return "observe_world"; }
        public JsonNode schema() { return schema("{}", "[]"); }
        public ToolResult execute(JsonNode args) {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayerEntity p = client.player;
            if (p == null || client.world == null) return ToolResult.failed("not in a world");
            ObjectNode o = JSON.createObjectNode();
            o.put("dimension", client.world.getRegistryKey().getValue().toString());
            o.put("x", p.getX()); o.put("y", p.getY()); o.put("z", p.getZ());
            o.put("health", p.getHealth()); o.put("food", p.getHungerManager().getFoodLevel());
            o.put("air", p.getAir()); o.put("submerged", p.isSubmergedInWater());
            o.put("on_ground", p.isOnGround());
            var inv = o.putObject("inventory");
            for (int i = 0; i < p.getInventory().size(); i++) {
                ItemStack stack = p.getInventory().getStack(i);
                if (!stack.isEmpty()) inv.put(stack.getItem().toString(), inv.path(stack.getItem().toString()).asInt(0) + stack.getCount());
            }
            return ToolResult.completed(o);
        }
    }

    private static final class StopAllTool implements AgentTool {
        private final AltoClef mod;
        StopAllTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "stop_all"; }
        public JsonNode schema() { return schema("{}", "[]"); }
        public ToolResult execute(JsonNode args) { mod.cancelUserTask(); mod.getInputControls().releaseAll(); return ToolResult.completed(JSON.createObjectNode().put("stopped", true)); }
    }

    private static final class PressKeyTool implements AgentTool {
        private final AltoClef mod;
        PressKeyTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "press_key"; }
        public JsonNode schema() { return schema("{\"key\":{\"type\":\"string\"},\"action\":{\"type\":\"string\",\"enum\":[\"press\",\"hold\",\"release\"]}}", "[\"key\",\"action\"]"); }
        public ToolResult execute(JsonNode args) {
            try {
                Input key = Input.valueOf(args.path("key").asText().toUpperCase(Locale.ROOT));
                String action = args.path("action").asText("press").toLowerCase(Locale.ROOT);
                switch (action) { case "press" -> mod.getInputControls().tryPress(key); case "hold" -> mod.getInputControls().hold(key); case "release" -> mod.getInputControls().release(key); default -> { return ToolResult.failed("invalid action"); } }
                return ToolResult.completed(JSON.createObjectNode().put("key", key.name()).put("action", action));
            } catch (IllegalArgumentException e) { return ToolResult.failed("invalid key: " + args.path("key").asText()); }
        }
    }

    private static final class LookTool implements AgentTool {
        private final AltoClef mod;
        LookTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "look"; }
        public JsonNode schema() { return schema("{\"yaw\":{\"type\":\"number\"},\"pitch\":{\"type\":\"number\"}}", "[\"yaw\",\"pitch\"]"); }
        public ToolResult execute(JsonNode args) { mod.getInputControls().forceLook((float) args.path("yaw").asDouble(), (float) args.path("pitch").asDouble()); return ToolResult.completed(JSON.createObjectNode().put("yaw", args.path("yaw").asDouble()).put("pitch", args.path("pitch").asDouble())); }
    }

    private static final class AltoClefTaskTool implements AgentTool {
        private final AltoClef mod;
        AltoClefTaskTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "altoclef_task"; }
        public JsonNode schema() { return schema("{\"command\":{\"type\":\"string\",\"description\":\"One existing companion command\"}}", "[\"command\"]"); }
        public ToolResult execute(JsonNode args) {
            String owner = mod.getButler().getCurrentUser();
            if (owner == null) return ToolResult.failed("no authorized owner session");
            String command = args.path("command").asText("").trim();
            if (command.isBlank()) return ToolResult.failed("command is empty");
            final String[] response = {"queued"};
            mod.getCompanionOrchestrator().handle(owner, command, message -> response[0] = message);
            return ToolResult.completed(JSON.createObjectNode().put("message", response[0]));
        }
    }

    private static final class BaritoneGoalTool implements AgentTool {
        private final AltoClef mod;
        BaritoneGoalTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "baritone_goal"; }
        public JsonNode schema() { return schema("{\"x\":{\"type\":\"integer\"},\"y\":{\"type\":\"integer\"},\"z\":{\"type\":\"integer\"}}", "[\"x\",\"y\",\"z\"]"); }
        public ToolResult execute(JsonNode args) {
            if (mod.getPlayer() == null) return ToolResult.failed("not in a world");
            var goal = new GoalBlock(new BlockPos(args.path("x").asInt(), args.path("y").asInt(), args.path("z").asInt()));
            mod.getClientBaritone().getCustomGoalProcess().setGoalAndPath(goal);
            return ToolResult.running(JSON.createObjectNode().put("x", args.path("x").asInt()).put("y", args.path("y").asInt()).put("z", args.path("z").asInt()));
        }
    }

    private static final class ChatPrivateTool implements AgentTool {
        private final AltoClef mod;
        ChatPrivateTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "chat_private"; }
        public JsonNode schema() { return schema("{\"message\":{\"type\":\"string\"}}", "[\"message\"]"); }
        public ToolResult execute(JsonNode args) {
            String owner = mod.getButler().getCurrentUser();
            if (owner == null) return ToolResult.failed("no authorized owner session");
            mod.getButler().sendTo(owner, args.path("message").asText(""), MessagePriority.TIMELY);
            return ToolResult.completed(JSON.createObjectNode().put("sent", true));
        }
    }
}
