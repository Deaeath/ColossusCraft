package adris.altoclef.tasks.resources.wood;

import adris.altoclef.tasks.resources.ResourceItemTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

public class CollectWoodenSlabTask extends ResourceItemTask {
    public CollectWoodenSlabTask(Item[] targets, ItemTarget planks, int count) {
        super(new ItemTarget(targets, count));
    }

    public CollectWoodenSlabTask(int count) {
        super(new ItemTarget(WoodItemSets.SLABS, count));
    }
}
