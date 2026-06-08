package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.RecipeTarget;

public class CraftGenericManuallyTask extends Task {
    private final RecipeTarget target;

    public CraftGenericManuallyTask(RecipeTarget target) {
        this.target = target;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return new CraftGenericWithRecipeBooksTask(target);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CraftGenericManuallyTask task && task.target.equals(target);
    }

    @Override
    protected String toDebugString() {
        return "Craft manual " + target;
    }
}
