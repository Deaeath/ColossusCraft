package adris.altoclef.tasks.resources.wood;

import adris.altoclef.tasks.resources.ResourceItemTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

public class CollectSignTask extends ResourceItemTask {
    public CollectSignTask(Item[] targets, ItemTarget planks, int count) {
        super(new ItemTarget(targets, count));
    }

    public CollectSignTask(int count) {
        super(new ItemTarget(WoodItemSets.SIGNS, count));
    }
}
