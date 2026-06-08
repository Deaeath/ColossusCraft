package adris.altoclef.tasks.resources;

import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class GetBuildingMaterialsTask extends CollectItemTask {
    public GetBuildingMaterialsTask(int count) {
        super(new ItemTarget(new Item[]{Items.DIRT, Items.COBBLESTONE, Items.NETHERRACK}, count));
    }
}
