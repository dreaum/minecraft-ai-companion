package adris.altoclef.companion;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.entity.GiveItemToPlayerTask;
import adris.altoclef.tasks.movement.FollowPlayerTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Adapts approved companion intents to AltoClef tasks. */
final class CompanionTaskFactory {

    private CompanionTaskFactory() {
    }

    static String validate(CompanionIntent intent) {
        return switch (intent.type()) {
            case COLLECT, CRAFT, SMELT, GIVE -> TaskCatalogue.taskExists(intent.target())
                    ? null : "I cannot obtain the item '" + intent.target() + "'.";
            case ATTACK -> validateAttackTarget(intent.target());
            default -> null;
        };
    }

    static Task create(AltoClef mod, CompanionIntent intent, String owner) {
        return switch (intent.type()) {
            // The catalogue owns the complete resource chain: tools, mining, crafting, fuel and smelting.
            case COLLECT, CRAFT, SMELT -> TaskCatalogue.getItemTask(intent.target(), intent.count());
            case GOTO -> new GetToBlockTask(new BlockPos(intent.x(), intent.y(), intent.z()));
            case FOLLOW -> new FollowPlayerTask(owner);
            case COME -> new ComeToPlayerTask(mod.getEntityTracker().getPlayerEntity(owner)
                    .orElseThrow(() -> new IllegalStateException("I cannot see " + owner + " yet.")), 2);
            case HOME -> new GetToBlockTask(mod.getModSettings().getHomeBasePosition());
            case ATTACK -> new KillEntityCountTask(resolveEntity(intent.target()), intent.count());
            case PROTECT -> new ProtectPlayerTask(owner);
            case GIVE -> existingItemDelivery(mod, owner, intent);
            default -> throw new IllegalArgumentException("Intent does not create a task: " + intent.type());
        };
    }

    private static Task existingItemDelivery(AltoClef mod, String owner, CompanionIntent intent) {
        ItemTarget target = TaskCatalogue.getItemTarget(intent.target(), intent.count());
        if (!StorageHelper.itemTargetsMet(mod, target)) {
            throw new IllegalStateException("I do not currently have " + intent.count() + " " + intent.target() + " to give.");
        }
        return new GiveItemToPlayerTask(owner, target);
    }

    private static EntityType<?> resolveEntity(String name) {
        Identifier id = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
        return id != null && Registries.ENTITY_TYPE.containsId(id) ? Registries.ENTITY_TYPE.get(id) : null;
    }

    private static String validateAttackTarget(String name) {
        EntityType<?> entity = resolveEntity(name);
        if (entity == null) {
            return "I do not recognize the entity '" + name + "'.";
        }
        if (!LivingEntity.class.isAssignableFrom(entity.getBaseClass())) {
            return "I can only attack living entities, not '" + name + "'.";
        }
        return null;
    }
}
