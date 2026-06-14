package adris.altoclef.tasks.construction;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class ClearRegionTask extends Task {
    private final BlockPos a;
    private final BlockPos b;

    public ClearRegionTask(BlockPos a, BlockPos b) {
        this.a = a;
        this.b = b;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (mod.getWorld() == null) return null;
        BlockPos player = mod.getPlayer() == null ? null : mod.getPlayer().blockPosition();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos scan : BlockPos.betweenClosed(a, b)) {
            BlockPos pos = scan.immutable();
            if (!mod.getWorld().getBlockState(pos).isAir()) {
                double dist = player == null ? 0.0 : player.distSqr(pos);
                if (nearest == null || dist < nearestDist) {
                    nearest = pos;
                    nearestDist = dist;
                }
            }
        }
        return nearest == null ? null : new DestroyBlockTask(nearest);
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        if (mod.getWorld() == null) return false;
        for (BlockPos scan : BlockPos.betweenClosed(a, b)) {
            if (!mod.getWorld().getBlockState(scan).isAir()) return false;
        }
        return true;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ClearRegionTask task && task.a.equals(a) && task.b.equals(b);
    }

    @Override
    protected String toDebugString() {
        return "Clear region";
    }
}
