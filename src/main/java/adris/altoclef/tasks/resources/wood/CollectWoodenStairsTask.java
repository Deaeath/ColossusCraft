package adris.altoclef.tasks.resources.wood;

import adris.altoclef.tasks.resources.ResourceItemTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

public class CollectWoodenStairsTask extends ResourceItemTask {
    public CollectWoodenStairsTask(Item[] targets, ItemTarget planks, int count) {
        super(new ItemTarget(targets, count));
    }

    public CollectWoodenStairsTask(int count) {
        super(new ItemTarget(WoodItemSets.STAIRS, count));
    }
}
