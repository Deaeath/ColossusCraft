package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RecipeTarget;

public class CraftInInventoryTask extends ResourceTask {
    private final RecipeTarget target;
    private final boolean collect;
    private final boolean ignoreUncataloguedSlots;

    public CraftInInventoryTask(RecipeTarget target, boolean collect, boolean ignoreUncataloguedSlots) {
        super(new ItemTarget(target.getOutputItem(), target.getTargetCount()));
        this.target = target;
        this.collect = collect;
        this.ignoreUncataloguedSlots = ignoreUncataloguedSlots;
    }

    public CraftInInventoryTask(RecipeTarget target) {
        this(target, true, false);
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        if (collect) {
            Task missing = collectMissingItemsTask(mod);
            if (missing != null) return missing;
        }
        setDebugState("Craft inventory ignore=" + ignoreUncataloguedSlots);
        return new CraftGenericWithRecipeBooksTask(target);
    }
}
