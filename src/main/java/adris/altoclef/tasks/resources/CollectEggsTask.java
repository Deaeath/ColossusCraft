package adris.altoclef.tasks.resources;

import net.minecraft.world.item.Items;

public class CollectEggsTask extends ResourceItemTask {
    public CollectEggsTask(int count) {
        super(Items.EGG, count);
    }
}
