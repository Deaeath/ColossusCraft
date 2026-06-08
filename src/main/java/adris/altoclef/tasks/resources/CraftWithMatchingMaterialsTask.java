package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.util.ItemTarget;

public abstract class CraftWithMatchingMaterialsTask extends ResourceTask {
    private final ItemTarget target;
    private final CraftingRecipe recipe;
    private final boolean[] sameMask;

    public CraftWithMatchingMaterialsTask(ItemTarget target, CraftingRecipe recipe, boolean[] sameMask) {
        super(target);
        this.target = target;
        this.recipe = recipe;
        this.sameMask = sameMask;
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        setDebugState("Craft matching materials mask=" + sameMask.length + " recipe=" + recipe);
        return collectMissingItemsTask(mod);
    }

    protected int getExpectedTotalCountOfSameItem(AltoClef mod, net.minecraft.world.item.Item sameItem) {
        return mod.getItemStorage().getItemCount(sameItem);
    }

    protected Task getSpecificSameResourceTask(AltoClef mod, net.minecraft.world.item.Item[] toGet) {
        return null;
    }

    protected net.minecraft.world.item.Item getSpecificItemCorrespondingToMajorityResource(net.minecraft.world.item.Item majority) {
        return target.getMatches().length == 0 ? null : target.getMatches()[0];
    }
}
