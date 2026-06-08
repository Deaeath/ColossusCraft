package adris.altoclef.tasks.resources;

import net.minecraft.world.item.Items;

public class CollectMilkTask extends ResourceItemTask {
    public CollectMilkTask(int count) {
        super(Items.MILK_BUCKET, count);
    }
}
