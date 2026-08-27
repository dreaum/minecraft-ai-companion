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
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;

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
        registry.register(new MoveTool(mod));
        registry.register(new InventoryTool());
        registry.register(new AttackEntityTool(mod));
        registry.register(new InteractBlockTool(mod));
        registry.register(new BaritoneCancelTool(mod));
        registry.register(new TutorialSearchTool(mod));
        registry.register(new TutorialReadTool(mod));
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
        public JsonNode schema() { return BuiltinAgentTools.schema("{}", "[]"); }
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
        public JsonNode schema() { return BuiltinAgentTools.schema("{}", "[]"); }
        public ToolResult execute(JsonNode args) { mod.cancelUserTask(); mod.getInputControls().releaseAll(); return ToolResult.completed(JSON.createObjectNode().put("stopped", true)); }
    }

    private static final class PressKeyTool implements AgentTool {
        private final AltoClef mod;
        PressKeyTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "press_key"; }
        public JsonNode schema() { return BuiltinAgentTools.schema("{\"key\":{\"type\":\"string\"},\"action\":{\"type\":\"string\",\"enum\":[\"press\",\"hold\",\"release\"]}}", "[\"key\",\"action\"]"); }
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
        public JsonNode schema() { return BuiltinAgentTools.schema("{\"yaw\":{\"type\":\"number\"},\"pitch\":{\"type\":\"number\"}}", "[\"yaw\",\"pitch\"]"); }
        public ToolResult execute(JsonNode args) { mod.getInputControls().forceLook((float) args.path("yaw").asDouble(), (float) args.path("pitch").asDouble()); return ToolResult.completed(JSON.createObjectNode().put("yaw", args.path("yaw").asDouble()).put("pitch", args.path("pitch").asDouble())); }
    }

    private static final class AltoClefTaskTool implements AgentTool {
        private final AltoClef mod;
        AltoClefTaskTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "altoclef_task"; }
        public JsonNode schema() { return BuiltinAgentTools.schema("{\"command\":{\"type\":\"string\",\"description\":\"One existing companion command\"}}", "[\"command\"]"); }
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
        public JsonNode schema() { return BuiltinAgentTools.schema("{\"x\":{\"type\":\"integer\"},\"y\":{\"type\":\"integer\"},\"z\":{\"type\":\"integer\"}}", "[\"x\",\"y\",\"z\"]"); }
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
        public JsonNode schema() { return BuiltinAgentTools.schema("{\"message\":{\"type\":\"string\"}}", "[\"message\"]"); }
        public ToolResult execute(JsonNode args) {
            String owner = mod.getButler().getCurrentUser();
            if (owner == null) return ToolResult.failed("no authorized owner session");
            mod.getButler().sendPublic(args.path("message").asText(""), MessagePriority.TIMELY);
            return ToolResult.completed(JSON.createObjectNode().put("sent", true));
        }
    }

    private static final class MoveTool implements AgentTool {
        private final AltoClef mod;
        MoveTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "move"; }
        public JsonNode schema() { return BuiltinAgentTools.schema("{\"direction\":{\"type\":\"string\",\"enum\":[\"MOVE_FORWARD\",\"MOVE_BACK\",\"MOVE_LEFT\",\"MOVE_RIGHT\",\"JUMP\",\"SNEAK\",\"SPRINT\"]},\"action\":{\"type\":\"string\",\"enum\":[\"hold\",\"release\",\"press\"]}}", "[\"direction\",\"action\"]"); }
        public ToolResult execute(JsonNode args) {
            try {
                String direction = args.path("direction").asText("").toUpperCase(Locale.ROOT);
                direction = switch (direction) {
                    case "FORWARD" -> "MOVE_FORWARD"; case "BACK" -> "MOVE_BACK";
                    case "LEFT" -> "MOVE_LEFT"; case "RIGHT" -> "MOVE_RIGHT";
                    default -> direction;
                };
                Input key = Input.valueOf(direction);
                String action = args.path("action").asText("press").toLowerCase(Locale.ROOT);
                switch (action) { case "hold" -> mod.getInputControls().hold(key); case "release" -> mod.getInputControls().release(key); case "press" -> mod.getInputControls().tryPress(key); default -> { return ToolResult.failed("invalid action"); } }
                return ToolResult.completed(JSON.createObjectNode().put("direction", key.name()).put("action", action));
            } catch (IllegalArgumentException e) { return ToolResult.failed("invalid direction"); }
        }
    }

    private static final class InventoryTool implements AgentTool {
        public String name() { return "inventory"; }
        public JsonNode schema() { return BuiltinAgentTools.schema("{}", "[]"); }
        public ToolResult execute(JsonNode args) {
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p == null) return ToolResult.failed("not in a world");
            ObjectNode out = JSON.createObjectNode();
            for (int i = 0; i < p.getInventory().size(); i++) { ItemStack s = p.getInventory().getStack(i); if (!s.isEmpty()) out.put(s.getItem().toString(), out.path(s.getItem().toString()).asInt(0) + s.getCount()); }
            return ToolResult.completed(out);
        }
    }

    private static final class AttackEntityTool implements AgentTool {
        private final AltoClef mod;
        AttackEntityTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "attack_entity"; }
        public JsonNode schema() { return BuiltinAgentTools.schema("{\"entity_id\":{\"type\":\"integer\"}}", "[\"entity_id\"]"); }
        public ToolResult execute(JsonNode args) {
            MinecraftClient client = MinecraftClient.getInstance(); Entity entity = client.world == null ? null : client.world.getEntityById(args.path("entity_id").asInt());
            if (entity == null) return ToolResult.failed("entity not found");
            if (!mod.getPlayer().isInRange(entity, mod.getModSettings().getEntityReachRange())) return ToolResult.failed("entity out of range");
            mod.getPlayerExtraController().attack(entity);
            return ToolResult.completed(JSON.createObjectNode().put("entity_id", entity.getId()));
        }
    }

    private static final class InteractBlockTool implements AgentTool {
        private final AltoClef mod;
        InteractBlockTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "interact_block"; }
        public JsonNode schema() { return BuiltinAgentTools.schema("{\"x\":{\"type\":\"integer\"},\"y\":{\"type\":\"integer\"},\"z\":{\"type\":\"integer\"},\"face\":{\"type\":\"string\"}}", "[\"x\",\"y\",\"z\"]"); }
        public ToolResult execute(JsonNode args) {
            if (MinecraftClient.getInstance().player == null || MinecraftClient.getInstance().interactionManager == null) return ToolResult.failed("not in a world");
            BlockPos pos = new BlockPos(args.path("x").asInt(), args.path("y").asInt(), args.path("z").asInt());
            Direction face; try { face = Direction.valueOf(args.path("face").asText("UP").toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { face = Direction.UP; }
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), face, pos, false);
            var result = MinecraftClient.getInstance().interactionManager.interactBlock(MinecraftClient.getInstance().player, Hand.MAIN_HAND, hit);
            return ToolResult.completed(JSON.createObjectNode().put("result", result.name()));
        }
    }

    private static final class BaritoneCancelTool implements AgentTool {
        private final AltoClef mod;
        BaritoneCancelTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "baritone_cancel"; }
        public JsonNode schema() { return BuiltinAgentTools.schema("{}", "[]"); }
        public ToolResult execute(JsonNode args) { mod.getClientBaritone().getPathingBehavior().forceCancel(); mod.getClientBaritone().getCustomGoalProcess().setGoal(null); return ToolResult.completed(JSON.createObjectNode().put("cancelled", true)); }
    }

    private static final class TutorialSearchTool implements AgentTool {
        private final AltoClef mod;
        TutorialSearchTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "search_tutorial"; }
        public JsonNode schema() { return BuiltinAgentTools.schema("{\"query\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"}}", "[\"query\"]"); }
        public ToolResult execute(JsonNode args) {
            if (mod.getTutorialIndex() == null) return ToolResult.failed("tutorial index unavailable");
            try { var hits = mod.getTutorialIndex().search(args.path("query").asText(), args.path("limit").asInt(5)); var out = JSON.createArrayNode(); for (var hit : hits) out.add(JSON.createObjectNode().put("id", hit.id()).put("title", hit.title()).put("path", hit.path()).put("snippet", hit.snippet())); return ToolResult.completed(out); }
            catch (java.sql.SQLException e) { return ToolResult.failed("tutorial search failed: " + e.getMessage()); }
        }
    }

    private static final class TutorialReadTool implements AgentTool {
        private final AltoClef mod;
        TutorialReadTool(AltoClef mod) { this.mod = mod; }
        public String name() { return "read_tutorial"; }
        public JsonNode schema() { return BuiltinAgentTools.schema("{\"id\":{\"type\":\"string\"}}", "[\"id\"]"); }
        public ToolResult execute(JsonNode args) {
            if (mod.getTutorialIndex() == null) return ToolResult.failed("tutorial index unavailable");
            try { return ToolResult.completed(JSON.createObjectNode().put("id", args.path("id").asText()).put("content", mod.getTutorialIndex().read(args.path("id").asText()))); }
            catch (java.io.IOException e) { return ToolResult.failed(e.getMessage()); }
        }
    }
}
