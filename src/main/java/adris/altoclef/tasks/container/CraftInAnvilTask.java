package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;

public class CraftInAnvilTask extends Task {
    private final ItemTarget target;

    public CraftInAnvilTask(ItemTarget target) {
        this.target = target;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        setDebugState("Anvil craft " + target);
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CraftInAnvilTask task && task.target.equals(target);
    }

    @Override
    protected String toDebugString() {
        return "Craft in anvil";
    }
}
