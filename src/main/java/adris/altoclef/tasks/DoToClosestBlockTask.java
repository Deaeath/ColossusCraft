package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Optional;

public class DoToClosestBlockTask extends AbstractDoToClosestObjectTask<BlockPos> {
    private final Block[] blocks;

    public DoToClosestBlockTask(Block... blocks) {
        this.blocks = blocks;
    }

    @Override
    protected Vec3 getPos(AltoClef mod, BlockPos obj) {
        return obj.getCenter();
    }

    @Override
    protected Optional<BlockPos> getClosestTo(AltoClef mod, Vec3 pos) {
        return mod.getBlockTracker().getNearestTracking(pos, blocks);
    }

    @Override
    protected Vec3 getOriginPos(AltoClef mod) {
        return mod.getPlayer() == null ? Vec3.ZERO : mod.getPlayer().position();
    }

    @Override
    protected Task getGoalTask(BlockPos obj) {
        return new DestroyBlockTask(obj);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof DoToClosestBlockTask task && Arrays.equals(task.blocks, blocks);
    }

    @Override
    protected String toDebugString() {
        return "Do to closest block";
    }
}
