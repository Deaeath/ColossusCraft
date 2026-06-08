package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class GetCloseToBlockTask extends Task {
    private final BlockPos toApproach;

    public GetCloseToBlockTask(BlockPos toApproach) {
        this.toApproach = toApproach;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return new GetWithinRangeOfBlockTask(toApproach, 3);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GetCloseToBlockTask task && task.toApproach.equals(toApproach);
    }

    @Override
    protected String toDebugString() {
        return "Approach " + toApproach.toShortString();
    }
}
