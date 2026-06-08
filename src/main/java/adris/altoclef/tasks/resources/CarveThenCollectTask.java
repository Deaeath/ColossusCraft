package adris.altoclef.tasks.resources;

import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

public class CarveThenCollectTask extends CollectItemTask {
    public CarveThenCollectTask(Item toCarve, Item result, int count) {
        super(new ItemTarget(result, count));
    }
}
