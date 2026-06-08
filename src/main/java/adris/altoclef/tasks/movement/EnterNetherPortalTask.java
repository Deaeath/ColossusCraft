package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;

import java.util.function.Predicate;

public class EnterNetherPortalTask extends Task {
    private final Task buildPortalTask;
    private final Dimension target;
    private final Predicate<BlockPos> portalPredicate;

    public EnterNetherPortalTask(Task buildPortalTask, Dimension target) {
        this(buildPortalTask, target, pos -> true);
    }

    public EnterNetherPortalTask(Task buildPortalTask, Dimension target, Predicate<BlockPos> portalPredicate) {
        this.buildPortalTask = buildPortalTask;
        this.target = target;
        this.portalPredicate = portalPredicate;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (target == WorldHelper.getCurrentDimension()) return null;
        if (buildPortalTask != null && !buildPortalTask.isFinished(mod)) {
            return buildPortalTask;
        }
        return new DefaultGoToDimensionTask(target);
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return target == WorldHelper.getCurrentDimension();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EnterNetherPortalTask task && task.target == target && task.portalPredicate == portalPredicate;
    }

    @Override
    protected String toDebugString() {
        return "Enter portal to " + target;
    }
}
