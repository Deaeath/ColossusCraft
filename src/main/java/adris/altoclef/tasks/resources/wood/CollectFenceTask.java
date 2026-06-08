package adris.altoclef.tasks.resources.wood;

import adris.altoclef.tasks.resources.ResourceItemTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

public class CollectFenceTask extends ResourceItemTask {
    public CollectFenceTask(Item[] targets, ItemTarget planks, int count) {
        super(new ItemTarget(targets, count));
    }

    public CollectFenceTask(int count) {
        super(new ItemTarget(WoodItemSets.FENCES, count));
    }
}
