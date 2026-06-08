package adris.altoclef.tasks.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public class PlaceStructureBlockTask extends PlaceBlockTask {
    public PlaceStructureBlockTask(BlockPos target) {
        super(target, Blocks.STRUCTURE_BLOCK);
    }
}
