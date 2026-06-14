package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

public class GetToBlockTask extends Task {
    private final BlockPos target;
    private final Dimension dimension;
    public GetToBlockTask(BlockPos target) {
        this(target, null);
    }

    // Upstream's "preferStairs" flag isn't used by our pathing; accept it for API parity.
    public GetToBlockTask(BlockPos target, boolean preferStairs) {
        this(target, null);
    }

    public GetToBlockTask(BlockPos target, Dimension dimension) {
        this.target = target;
        this.dimension = dimension;
    }

    @Override
    protected void onStart(AltoClef mod) {}

    @Override
    protected Task onTick(AltoClef mod) {
        if (dimension != null && dimension != WorldHelper.getCurrentDimension()) {
            return new DefaultGoToDimensionTask(dimension);
        }
        Task clear = clearPathClutter(mod);
        if (clear != null) {
            setDebugState("Clear path clutter near " + target.toShortString());
            return clear;
        }
        if (!mod.getClientBaritone().getPathingBehavior().isPathing()) {
            mod.runBaritone("goto " + target.getX() + " " + target.getY() + " " + target.getZ());
        }
        setDebugState("Goto " + target.toShortString());
        return null;
    }

    private Task clearPathClutter(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return null;
        BlockPos player = mod.getPlayer().blockPosition();
        int sx = Integer.compare(target.getX(), player.getX());
        int sz = Integer.compare(target.getZ(), player.getZ());
        BlockPos[] centers = {
                player,
                player.offset(sx, 0, sz),
                player.offset(sx, 0, 0),
                player.offset(0, 0, sz)
        };
        for (BlockPos center : centers) {
            for (int y = player.getY(); y <= player.getY() + 1; y++) {
                for (int x = center.getX() - 1; x <= center.getX() + 1; x++) {
                    for (int z = center.getZ() - 1; z <= center.getZ() + 1; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = mod.getWorld().getBlockState(pos);
                        if (isPathClutter(state)) return new DestroyBlockTask(pos);
                    }
                }
            }
        }
        return null;
    }

    private static boolean isPathClutter(BlockState state) {
        if (state == null || state.isAir() || !state.getFluidState().isEmpty()) return false;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.equals("decorated_pot")
                || path.equals("flower_pot")
                || path.startsWith("potted_")
                || (path.contains("pot") && !path.contains("potato"));
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return mod.getPlayer() != null
                && (dimension == null || dimension == WorldHelper.getCurrentDimension())
                && mod.getPlayer().blockPosition().distSqr(target) <= 4.0;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GetToBlockTask task
                && task.target.equals(target)
                && task.dimension == dimension;
    }

    @Override
    protected String toDebugString() {
        return "Get to block " + target.toShortString();
    }
}
