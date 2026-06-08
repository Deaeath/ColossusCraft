package adris.altoclef.tasks.slot;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.StorageHelper;

public class EnsureFreeCursorSlotTask extends Task {
    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return StorageHelper.getItemStackInCursorSlot().isEmpty() ? null : new ThrowCursorTask();
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.getItemStackInCursorSlot().isEmpty();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EnsureFreeCursorSlotTask;
    }

    @Override
    protected String toDebugString() {
        return "Ensure free cursor";
    }
}
