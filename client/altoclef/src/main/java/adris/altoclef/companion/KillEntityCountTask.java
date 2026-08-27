package adris.altoclef.companion;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;

import java.util.Optional;

/** Kills a fixed number of living entities without treating despawns as completed kills. */
final class KillEntityCountTask extends Task {

    private final EntityType<?> entityType;
    private final int requestedKills;
    private Entity currentTarget;
    private int confirmedKills;

    KillEntityCountTask(EntityType<?> entityType, int requestedKills) {
        this.entityType = entityType;
        this.requestedKills = requestedKills;
    }

    @Override
    public boolean isFinished() {
        return confirmedKills >= requestedKills;
    }

    @Override
    protected void onStart() {
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (currentTarget != null) {
            if (currentTarget instanceof LivingEntity living && living.isDead()) {
                confirmedKills++;
                currentTarget = null;
            } else if (!currentTarget.isAlive()) {
                // A despawn, unload, or invalidation is not evidence that we killed it.
                currentTarget = null;
            } else {
                setDebugState("Killing " + entityType.getTranslationKey() + " ("
                        + confirmedKills + "/" + requestedKills + ")");
                return new KillEntityTask(currentTarget);
            }
        }

        if (isFinished()) {
            return null;
        }

        Optional<Entity> nextTarget = mod.getEntityTracker().getClosestEntity(
                mod.getPlayer().getPos(), entity -> entity.getType() == entityType,
                Entity.class);
        if (nextTarget.isPresent()) {
            currentTarget = nextTarget.get();
            return new KillEntityTask(currentTarget);
        }

        setDebugState("Searching for " + entityType.getTranslationKey() + " ("
                + confirmedKills + "/" + requestedKills + ")");
        return new TimeoutWanderTask();
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof KillEntityCountTask task
                && entityType == task.entityType
                && requestedKills == task.requestedKills;
    }

    @Override
    protected String toDebugString() {
        return "Kill " + requestedKills + " " + entityType.getTranslationKey();
    }
}
