package adris.altoclef.tasks.resources;

import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class CollectFlowerTask extends ResourceItemTask {
    private static final Item[] FLOWERS = {
            Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM, Items.AZURE_BLUET,
            Items.RED_TULIP, Items.ORANGE_TULIP, Items.WHITE_TULIP, Items.PINK_TULIP,
            Items.OXEYE_DAISY, Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY, Items.WITHER_ROSE,
            Items.SUNFLOWER, Items.LILAC, Items.ROSE_BUSH, Items.PEONY
    };

    public CollectFlowerTask(int count) {
        super(new ItemTarget(FLOWERS, count));
    }
}
