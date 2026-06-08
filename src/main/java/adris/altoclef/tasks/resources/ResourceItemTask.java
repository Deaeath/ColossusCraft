package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

public abstract class ResourceItemTask extends ResourceTask {
    protected ResourceItemTask(Item item, int count) {
        super(item, count);
    }

    protected ResourceItemTask(ItemTarget... targets) {
        super(targets);
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        return collectMissingItemsTask(mod);
    }
}
