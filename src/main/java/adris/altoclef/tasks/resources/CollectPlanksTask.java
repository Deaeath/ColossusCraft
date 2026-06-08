package adris.altoclef.tasks.resources;

import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class CollectPlanksTask extends ResourceItemTask {
    private static final Item[] PLANKS = {
            Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS,
            Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS,
            Items.BAMBOO_PLANKS, Items.CRIMSON_PLANKS, Items.WARPED_PLANKS
    };

    public CollectPlanksTask(int count) {
        super(new ItemTarget(PLANKS, count));
    }
}
