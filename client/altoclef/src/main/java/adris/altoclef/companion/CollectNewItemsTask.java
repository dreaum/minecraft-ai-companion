package adris.altoclef.companion;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.item.ItemStack;

/** Collects an amount relative to the inventory state when the command starts. */
final class CollectNewItemsTask extends Task {

    private final String targetName;
    private final int amountToAdd;
    private ItemTarget target;
    private int startingCount;
    private ResourceTask collectTask;

    CollectNewItemsTask(String targetName, int amountToAdd) {
        this.targetName = targetName;
        this.amountToAdd = amountToAdd;
    }

    @Override
    protected void onStart() {
        // Task.interrupt() causes onStart() to run again when the same task is
        // resumed. Keep the original inventory snapshot for that task instance.
        if (collectTask != null) {
            return;
        }
        target = TaskCatalogue.getItemTarget(targetName, amountToAdd);
        startingCount = inventoryCountWithoutCursor(AltoClef.getInstance());
        collectTask = TaskCatalogue.getItemTask(targetName, startingCount + amountToAdd);
    }

    @Override
    protected Task onTick() {
        return collectTask;
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    public boolean isFinished() {
        return target != null && inventoryCountWithoutCursor(AltoClef.getInstance()) >= startingCount + amountToAdd;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CollectNewItemsTask task
                && targetName.equals(task.targetName)
                && amountToAdd == task.amountToAdd;
    }

    @Override
    protected String toDebugString() {
        return "Collect new " + targetName + " x " + amountToAdd;
    }

    private int inventoryCountWithoutCursor(AltoClef mod) {
        int count = mod.getItemStorage().getItemCountInventoryOnly(target.getMatches());
        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
        return target.matches(cursor.getItem()) ? count - cursor.getCount() : count;
    }
}
