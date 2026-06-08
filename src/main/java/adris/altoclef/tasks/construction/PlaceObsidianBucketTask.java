package adris.altoclef.tasks.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public class PlaceObsidianBucketTask extends PlaceBlockTask {
    public PlaceObsidianBucketTask(BlockPos target) {
        super(target, Blocks.OBSIDIAN);
    }
}
