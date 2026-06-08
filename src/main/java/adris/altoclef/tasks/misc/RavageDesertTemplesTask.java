package adris.altoclef.tasks.misc;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.LocateDesertTempleTask;
import adris.altoclef.tasksystem.Task;

public class RavageDesertTemplesTask extends Task {
    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return new LocateDesertTempleTask();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof RavageDesertTemplesTask;
    }

    @Override
    protected String toDebugString() {
        return "Ravage desert temples";
    }
}
