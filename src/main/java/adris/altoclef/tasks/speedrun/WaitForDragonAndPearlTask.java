package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;

public class WaitForDragonAndPearlTask extends Task implements IDragonWaiter {
    @Override
    public Task getWaitTask(AltoClef mod) {
        return this;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        setDebugState("Wait for dragon perch");
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof WaitForDragonAndPearlTask;
    }

    @Override
    protected String toDebugString() {
        return "Wait for dragon";
    }
}
