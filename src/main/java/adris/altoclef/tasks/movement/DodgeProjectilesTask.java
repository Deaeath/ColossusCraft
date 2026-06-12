package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;

public class DodgeProjectilesTask extends Task {
    private final double distanceHorizontal;
    private final double distanceVertical;

    public DodgeProjectilesTask() {
        this(2, 10);
    }

    public DodgeProjectilesTask(double distanceHorizontal, double distanceVertical) {
        this.distanceHorizontal = distanceHorizontal;
        this.distanceVertical = distanceVertical;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return new RunAwayFromHostilesTask(8, true);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof DodgeProjectilesTask;
    }

    @Override
    protected String toDebugString() {
        return "Dodge projectiles";
    }
}
