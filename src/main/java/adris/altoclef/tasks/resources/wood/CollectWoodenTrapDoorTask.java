package adris.altoclef.tasks.resources.wood;

import adris.altoclef.tasks.resources.ResourceItemTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

public class CollectWoodenTrapDoorTask extends ResourceItemTask {
    public CollectWoodenTrapDoorTask(Item[] targets, ItemTarget planks, int count) {
        super(new ItemTarget(targets, count));
    }

    public CollectWoodenTrapDoorTask(int count) {
        super(new ItemTarget(WoodItemSets.TRAPDOORS, count));
    }
}
