package adris.altoclef.tasks.resources.wood;

import adris.altoclef.tasks.resources.ResourceItemTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

public class CollectWoodenPressurePlateTask extends ResourceItemTask {
    public CollectWoodenPressurePlateTask(Item[] targets, ItemTarget planks, int count) {
        super(new ItemTarget(targets, count));
    }

    public CollectWoodenPressurePlateTask(int count) {
        super(new ItemTarget(WoodItemSets.PRESSURE_PLATES, count));
    }
}
