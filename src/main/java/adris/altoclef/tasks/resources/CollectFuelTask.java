package adris.altoclef.tasks.resources;

import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Items;

public class CollectFuelTask extends ResourceItemTask {
    public CollectFuelTask(int count) {
        super(new ItemTarget(new net.minecraft.world.item.Item[]{Items.COAL, Items.CHARCOAL, Items.COAL_BLOCK}, count));
    }
}
