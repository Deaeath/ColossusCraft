package adris.altoclef.tasks.stupid;

import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public class ReplaceBlocksTask extends PlaceBlockTask {
    public ReplaceBlocksTask(ItemTarget toReplace, BlockPos from, BlockPos to, Block[] toFind) {
        super(from, toFind);
    }
}
