package adris.altoclef.companion;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.FollowPlayerTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

/** Stays with the owner and only engages nearby hostile mobs. */
final class ProtectPlayerTask extends Task {

    private static final double DEFENCE_RADIUS = 12.0D;
    private final String owner;

    ProtectPlayerTask(String owner) {
        this.owner = owner;
    }

    @Override
    protected void onStart() {
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        PlayerEntity protectedPlayer = mod.getEntityTracker().getPlayerEntity(owner).orElse(null);
        if (protectedPlayer == null) {
            setDebugState("Waiting for " + owner + " to enter render distance");
            return null;
        }
        setDebugState("Protecting " + owner);
        Box nearby = protectedPlayer.getBoundingBox().expand(DEFENCE_RADIUS);
        if (mod.getWorld().getEntitiesByClass(HostileEntity.class, nearby,
                entity -> isThreat(entity, protectedPlayer)).isEmpty()) {
            return new FollowPlayerTask(owner);
        }
        return new KillEntitiesTask(entity -> isThreat(entity, protectedPlayer), HostileEntity.class);
    }

    private static boolean isThreat(Entity entity, PlayerEntity protectedPlayer) {
        return entity instanceof HostileEntity && entity.isAlive()
                && entity.squaredDistanceTo(protectedPlayer) <= DEFENCE_RADIUS * DEFENCE_RADIUS;
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ProtectPlayerTask task && owner.equalsIgnoreCase(task.owner);
    }

    @Override
    protected String toDebugString() {
        return "Protecting " + owner;
    }
}
