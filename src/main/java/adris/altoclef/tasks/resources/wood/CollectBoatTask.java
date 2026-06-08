package adris.altoclef.tasks.resources.wood;

import adris.altoclef.tasks.resources.ResourceItemTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

public class CollectBoatTask extends ResourceItemTask {
    public CollectBoatTask(Item[] targets, ItemTarget planks, int count) {
        super(new ItemTarget(targets, count));
    }

    public CollectBoatTask(Item target, String plankCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
    }

    public CollectBoatTask(int count) {
        super(new ItemTarget(WoodItemSets.BOATS, count));
    }
}
