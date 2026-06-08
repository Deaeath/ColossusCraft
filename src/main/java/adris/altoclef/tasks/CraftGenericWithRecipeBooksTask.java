package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.container.CraftRecipeBookTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RecipeTarget;

public class CraftGenericWithRecipeBooksTask extends Task {
    private final RecipeTarget target;

    public CraftGenericWithRecipeBooksTask(RecipeTarget target) {
        this.target = target;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return new CraftRecipeBookTask(new ItemTarget(target.getOutputItem(), target.getTargetCount()), false);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CraftGenericWithRecipeBooksTask task && task.target.equals(target);
    }

    @Override
    protected String toDebugString() {
        return "Craft recipe-book " + target;
    }
}
