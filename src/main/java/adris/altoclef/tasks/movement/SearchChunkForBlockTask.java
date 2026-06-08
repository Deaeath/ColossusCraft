package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.Optional;

public class SearchChunkForBlockTask extends SearchChunksExploreTask {
    private final Block[] blocks;

    public SearchChunkForBlockTask(Block... blocks) {
        this.blocks = blocks;
    }

    @Override
    protected void onStart(AltoClef mod) {
        super.onStart(mod);
        mod.getBlockTracker().trackBlock(blocks);
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Optional<BlockPos> nearest = mod.getBlockTracker().getNearestTracking(blocks);
        if (nearest.isPresent()) {
            return new GetToBlockTask(nearest.get());
        }
        return super.onTick(mod);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(blocks);
        super.onStop(mod, interruptTask);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SearchChunkForBlockTask task && Arrays.equals(task.blocks, blocks);
    }

    @Override
    protected String toDebugString() {
        return "Search chunk for block " + Arrays.toString(blocks);
    }
}
