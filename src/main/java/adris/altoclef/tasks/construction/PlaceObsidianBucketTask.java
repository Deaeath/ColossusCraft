package adris.altoclef.tasks.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public class PlaceObsidianBucketTask extends PlaceBlockTask {
    private final BlockPos target;

    public PlaceObsidianBucketTask(BlockPos target) {
        super(target, Blocks.OBSIDIAN);
        this.target = target;
    }

    public BlockPos position() {
        return target;
    }
}
