package adris.altoclef.tasks.construction;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class ClearLiquidTask extends Task {
    private final BlockPos start;
    private final BlockPos end;

    public ClearLiquidTask(BlockPos start, BlockPos end) {
        this.start = start;
        this.end = end;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return new ClearRegionTask(start, end);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ClearLiquidTask task && task.start.equals(start) && task.end.equals(end);
    }

    @Override
    protected String toDebugString() {
        return "Clear liquid";
    }
}
