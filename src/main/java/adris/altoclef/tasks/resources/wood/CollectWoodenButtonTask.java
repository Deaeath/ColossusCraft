package adris.altoclef.tasks.resources.wood;

import adris.altoclef.tasks.resources.ResourceItemTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

public class CollectWoodenButtonTask extends ResourceItemTask {
    public CollectWoodenButtonTask(Item[] targets, ItemTarget planks, int count) {
        super(new ItemTarget(targets, count));
    }

    public CollectWoodenButtonTask(int count) {
        super(new ItemTarget(WoodItemSets.BUTTONS, count));
    }
}
