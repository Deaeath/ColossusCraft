package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;

public abstract class ResourceTask extends Task {
    protected final ItemTarget[] itemTargets;
    protected final ItemTarget[] _itemTargets;
    private Dimension forcedDimension;

    public ResourceTask(ItemTarget... itemTargets) {
        this.itemTargets = itemTargets;
        this._itemTargets = itemTargets;
    }

    public ResourceTask(Item item, int count) {
        this(new ItemTarget(item, count));
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.itemTargetsMetInventory(mod, itemTargets);
    }

    @Override
    protected void onStart(AltoClef mod) {
        onResourceStart(mod);
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (forcedDimension != null && WorldHelper.getCurrentDimension() != forcedDimension) {
            setDebugState("Go to " + forcedDimension);
            return new DefaultGoToDimensionTask(forcedDimension);
        }
        return onResourceTick(mod);
    }

    protected Task collectMissingItemsTask(AltoClef mod) {
        for (ItemTarget target : itemTargets) {
            if (!StorageHelper.itemTargetsMetInventory(mod, target)) {
                return new CollectItemTask(target);
            }
        }
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        onResourceStop(mod, interruptTask);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ResourceTask task
                && isEqualResource(task)
                && Arrays.equals(task.itemTargets, itemTargets);
    }

    @Override
    protected String toDebugString() {
        return toDebugStringName() + " " + Arrays.toString(itemTargets);
    }

    public ResourceTask mineIfPresent(Block[] toMine) {
        return this;
    }

    public ResourceTask forceDimension(Dimension dimension) {
        forcedDimension = dimension;
        return this;
    }

    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    protected void onResourceStart(AltoClef mod) {
    }

    protected abstract Task onResourceTick(AltoClef mod);

    protected void onResourceStop(AltoClef mod, Task interruptTask) {
    }

    protected boolean isEqualResource(ResourceTask other) {
        return other != null && other.getClass() == getClass();
    }

    protected String toDebugStringName() {
        return getClass().getSimpleName();
    }

    public ItemTarget[] getItemTargets() {
        return itemTargets;
    }
}
