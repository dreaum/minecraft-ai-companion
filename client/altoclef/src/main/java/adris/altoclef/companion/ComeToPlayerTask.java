package adris.altoclef.companion;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.player.PlayerEntity;

/** Moves to a visible player, then completes instead of continuously following them. */
public class ComeToPlayerTask extends Task {

    private final PlayerEntity target;
    private final double closeEnoughDistance;

    public ComeToPlayerTask(PlayerEntity target, double closeEnoughDistance) {
        this.target = target;
        this.closeEnoughDistance = closeEnoughDistance;
    }

    @Override
    protected void onStart() {
    }

    @Override
    protected Task onTick() {
        if (!target.isAlive()) {
            setDebugState("Target player is no longer available.");
            return null;
        }
        return new GetToEntityTask(target, closeEnoughDistance);
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    public boolean isFinished() {
        return target.isAlive() && AltoClef.getInstance().getPlayer().isInRange(target, closeEnoughDistance);
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof ComeToPlayerTask task) {
            return task.target.equals(target) && Math.abs(task.closeEnoughDistance - closeEnoughDistance) < 0.1;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Approaching " + target.getName().getString();
    }
}
