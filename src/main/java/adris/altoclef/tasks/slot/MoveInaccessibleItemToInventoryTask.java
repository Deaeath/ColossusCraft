package adris.altoclef.tasks.slot;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;

public class MoveInaccessibleItemToInventoryTask extends Task {
    private final ItemTarget target;

    public MoveInaccessibleItemToInventoryTask(ItemTarget target) {
        this.target = target;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        setDebugState("Move inaccessible " + target);
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof MoveInaccessibleItemToInventoryTask task && task.target.equals(target);
    }

    @Override
    protected String toDebugString() {
        return "Move inaccessible item";
    }
}
