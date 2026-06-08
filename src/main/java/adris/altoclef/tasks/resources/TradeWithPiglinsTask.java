package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.world.item.Items;

public class TradeWithPiglinsTask extends ResourceTask {
    private final int goldCount;
    private final ItemTarget target;

    public TradeWithPiglinsTask(int goldCount, ItemTarget target) {
        super(target);
        this.goldCount = goldCount;
        this.target = target;
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        if (!StorageHelper.itemTargetsMetInventory(mod, new ItemTarget(Items.GOLD_INGOT, goldCount))) {
            return new CollectItemTask(new ItemTarget(Items.GOLD_INGOT, goldCount));
        }
        if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
            return new DefaultGoToDimensionTask(Dimension.NETHER);
        }
        setDebugState("Barter fallback: collect " + target);
        return new CollectItemTask(target);
    }
}
