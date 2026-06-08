package adris.altoclef.tasks.resources;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class CollectBucketLiquidTask extends ResourceItemTask {
    public CollectBucketLiquidTask(String liquidName, Item filledBucket, int targetCount, Block toCollect) {
        super(filledBucket, targetCount);
    }
}
