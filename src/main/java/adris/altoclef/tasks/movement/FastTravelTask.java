package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class FastTravelTask extends Task {
    private final BlockPos target;
    private final Integer threshold;
    private final boolean collectPortalMaterialsIfAbsent;

    public FastTravelTask(BlockPos overworldTarget, Integer threshold, boolean collectPortalMaterialsIfAbsent) {
        this.target = overworldTarget;
        this.threshold = threshold;
        this.collectPortalMaterialsIfAbsent = collectPortalMaterialsIfAbsent;
    }

    public FastTravelTask(BlockPos overworldTarget, boolean collectPortalMaterialsIfAbsent) {
        this(overworldTarget, null, collectPortalMaterialsIfAbsent);
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return new GetToBlockTask(target);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof FastTravelTask task
                && task.target.equals(target)
                && java.util.Objects.equals(task.threshold, threshold)
                && task.collectPortalMaterialsIfAbsent == collectPortalMaterialsIfAbsent;
    }

    @Override
    protected String toDebugString() {
        return "Fast travel " + target.toShortString();
    }
}
