package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;

public class RunAwayFromHostilesTask extends Task {
    private final double distanceToRun;
    private final boolean includeSkeletons;

    public RunAwayFromHostilesTask(double distance, boolean includeSkeletons) {
        this.distanceToRun = distance;
        this.includeSkeletons = includeSkeletons;
    }

    public RunAwayFromHostilesTask(double distance) {
        this(distance, false);
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return new RunAwayFromEntitiesTask(() -> mod.getEntityTracker().getHostiles(), distanceToRun, 0.8) {
        };
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof RunAwayFromHostilesTask task && Math.abs(task.distanceToRun - distanceToRun) < 1 && task.includeSkeletons == includeSkeletons;
    }

    @Override
    protected String toDebugString() {
        return "Run from hostiles";
    }
}
