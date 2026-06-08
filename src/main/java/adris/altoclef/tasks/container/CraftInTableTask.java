package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CraftGenericWithRecipeBooksTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RecipeTarget;

import java.util.Arrays;

public class CraftInTableTask extends ResourceTask {
    private final RecipeTarget[] targets;
    private final boolean collect;
    private final boolean ignoreUncataloguedSlots;

    public CraftInTableTask(RecipeTarget[] targets) {
        super(extractItemTargets(targets));
        this.targets = targets;
        this.collect = true;
        this.ignoreUncataloguedSlots = false;
    }

    public CraftInTableTask(RecipeTarget target, boolean collect, boolean ignoreUncataloguedSlots) {
        super(new ItemTarget(target.getOutputItem(), target.getTargetCount()));
        this.targets = new RecipeTarget[]{target};
        this.collect = collect;
        this.ignoreUncataloguedSlots = ignoreUncataloguedSlots;
    }

    public CraftInTableTask(RecipeTarget target) {
        this(target, true, false);
    }

    private static ItemTarget[] extractItemTargets(RecipeTarget[] recipeTargets) {
        return Arrays.stream(recipeTargets)
                .map(target -> new ItemTarget(target.getOutputItem(), target.getTargetCount()))
                .toArray(ItemTarget[]::new);
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        if (collect) {
            Task missing = collectMissingItemsTask(mod);
            if (missing != null) return missing;
        }
        if (!adris.altoclef.util.helpers.StorageHelper.isBigCraftingOpen()) {
            return new OpenCraftingTableTask();
        }
        setDebugState("Craft table ignore=" + ignoreUncataloguedSlots);
        return new CraftGenericWithRecipeBooksTask(targets[0]);
    }
}
