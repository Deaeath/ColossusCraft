package adris.altoclef.tasks.resources.wood;

import adris.altoclef.tasks.resources.ResourceItemTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

public class CollectWoodenDoorTask extends ResourceItemTask {
    public CollectWoodenDoorTask(Item[] targets, ItemTarget planks, int count) {
        super(new ItemTarget(targets, count));
    }

    public CollectWoodenDoorTask(int count) {
        super(new ItemTarget(WoodItemSets.DOORS, count));
    }
}
