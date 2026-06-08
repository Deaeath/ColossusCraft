package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.util.ItemTarget;

public class CollectRecipeCataloguedResourcesTask extends ResourceTask {
    private final CraftingRecipe recipe;
    private final int count;

    public CollectRecipeCataloguedResourcesTask(CraftingRecipe recipe, int count) {
        super(java.util.Arrays.stream(recipe.getSlots()).filter(target -> target != null && !target.isEmpty()).toArray(ItemTarget[]::new));
        this.recipe = recipe;
        this.count = count;
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        setDebugState("Collect recipe resources x" + count);
        return collectMissingItemsTask(mod);
    }

    @Override
    protected String toDebugStringName() {
        return "Collect recipe resources " + recipe;
    }
}
