package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class ShearAndCollectBlockTask extends ResourceItemTask {
    public ShearAndCollectBlockTask(Item target, int count) {
        super(target, count);
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.SHEARS)) {
            return new CollectItemTask(new ItemTarget(Items.SHEARS, 1));
        }
        return super.onResourceTick(mod);
    }
}
