package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;

public class KillEnderDragonWithBedsTask extends KillEnderDragonTask {
    private final Task waiter;

    public KillEnderDragonWithBedsTask(Task waiter) {
        this.waiter = waiter;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof KillEnderDragonWithBedsTask;
    }
}
