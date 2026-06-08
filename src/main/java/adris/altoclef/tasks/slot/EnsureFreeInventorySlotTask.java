package adris.altoclef.tasks.slot;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;

public class EnsureFreeInventorySlotTask extends Task {
    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        setDebugState("Free inventory slot");
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return mod.getItemStorage().hasEmptyInventorySlot();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EnsureFreeInventorySlotTask;
    }

    @Override
    protected String toDebugString() {
        return "Ensure free inventory";
    }
}
