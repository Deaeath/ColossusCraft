package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class DoToClosestBlockTask extends AbstractDoToClosestObjectTask<BlockPos> {
    private final Block[] blocks;
    private final Supplier<Vec3> getOriginPos;
    private final Function<Vec3, Optional<BlockPos>> getClosest;
    private final Function<BlockPos, Task> getTargetTask;
    private final Predicate<BlockPos> isValid;

    public DoToClosestBlockTask(Block... blocks) {
        this(null, blocks);
    }

    public DoToClosestBlockTask(Supplier<Vec3> getOriginSupplier, Function<BlockPos, Task> getTargetTask, Function<Vec3, Optional<BlockPos>> getClosestBlock, Predicate<BlockPos> isValid, Block... blocks) {
        this.getOriginPos = getOriginSupplier;
        this.getTargetTask = getTargetTask;
        this.getClosest = getClosestBlock;
        this.isValid = isValid;
        this.blocks = blocks;
    }

    public DoToClosestBlockTask(Function<BlockPos, Task> getTargetTask, Function<Vec3, Optional<BlockPos>> getClosestBlock, Predicate<BlockPos> isValid, Block... blocks) {
        this(null, getTargetTask, getClosestBlock, isValid, blocks);
    }

    public DoToClosestBlockTask(Function<BlockPos, Task> getTargetTask, Block... blocks) {
        this(getTargetTask, blockPos -> true, blocks);
    }

    public DoToClosestBlockTask(Function<BlockPos, Task> getTargetTask, Predicate<BlockPos> isValid, Block... blocks) {
        this(null, getTargetTask, null, isValid, blocks);
    }

    @Override
    protected Vec3 getPos(AltoClef mod, BlockPos obj) {
        return WorldHelper.toVec3d(obj);
    }

    @Override
    protected Optional<BlockPos> getClosestTo(AltoClef mod, Vec3 pos) {
        if (getClosest != null) return getClosest.apply(pos);
        return mod.getBlockTracker().getNearestTracking(pos, isValid, blocks);
    }

    @Override
    protected Vec3 getOriginPos(AltoClef mod) {
        if (getOriginPos != null) return getOriginPos.get();
        return mod.getPlayer() == null ? Vec3.ZERO : mod.getPlayer().position();
    }

    @Override
    protected Task getGoalTask(BlockPos obj) {
        if (getTargetTask != null) {
            return getTargetTask.apply(obj);
        }
        return new DestroyBlockTask(obj);
    }

    @Override
    protected boolean isValid(AltoClef mod, BlockPos obj) {
        if (!mod.getChunkTracker().isChunkLoaded(obj)) return true;
        if (isValid != null && !isValid.test(obj)) return false;
        return mod.getBlockTracker().blockIsValid(obj, blocks);
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
