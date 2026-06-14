package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public abstract class SearchChunksExploreTask extends Task {
    private SpiralSearchTask _spiral;

    @Override
    protected void onStart(AltoClef mod) {
        _spiral = null;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (_spiral == null) {
            BlockPos origin = new BlockPos(
                (int) mod.getPlayer().getX(),
                (int) mod.getPlayer().getY(),
                (int) mod.getPlayer().getZ());
            _spiral = new SpiralSearchTask(origin);
        }
        setDebugState("Spiral explore chunks");
        return _spiral;
    }

    protected boolean isChunkWithinSearchSpace(AltoClef mod, ChunkPos pos) {
        return true;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }
}
