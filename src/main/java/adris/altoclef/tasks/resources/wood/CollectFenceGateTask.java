package adris.altoclef.tasks.resources.wood;

import adris.altoclef.tasks.resources.ResourceItemTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

public class CollectFenceGateTask extends ResourceItemTask {
    public CollectFenceGateTask(Item[] targets, ItemTarget planks, int count) {
        super(new ItemTarget(targets, count));
    }

    public CollectFenceGateTask(int count) {
        super(new ItemTarget(WoodItemSets.FENCE_GATES, count));
    }
}
