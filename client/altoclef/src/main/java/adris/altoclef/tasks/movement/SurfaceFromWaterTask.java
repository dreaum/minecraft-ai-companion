package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import baritone.api.utils.input.Input;

/** Reaches breathable water surface without abandoning the current route for the shore. */
public class SurfaceFromWaterTask extends Task {

    @Override
    protected void onStart() {
    }

    @Override
    protected Task onTick() {
        AltoClef.getInstance().getInputControls().hold(Input.JUMP);
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef.getInstance().getInputControls().release(Input.JUMP);
    }

    @Override
    public boolean isFinished() {
        return !AltoClef.getInstance().getPlayer().isSubmergedInWater();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SurfaceFromWaterTask;
    }

    @Override
    protected String toDebugString() {
        return "Surfacing for air";
    }
}
