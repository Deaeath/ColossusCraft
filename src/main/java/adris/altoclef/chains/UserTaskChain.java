package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.TaskFinishedEvent;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.time.Stopwatch;

public class UserTaskChain extends SingleTaskChain {

    private final Stopwatch taskStopwatch = new Stopwatch();
    private Runnable currentOnFinish;
    private boolean currentPrioritizesOverMobDefense;

    public UserTaskChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    protected void onTick(AltoClef mod) {
        if (!AltoClef.inGame()) return;
        super.onTick(mod);
    }

    public void cancel(AltoClef mod) {
        if (mainTask != null && mainTask.isActive()) {
            stop(mod);
            onTaskFinish(mod);
        }
    }

    @Override
    public float getPriority(AltoClef mod) {
        return 50;
    }

    public boolean shouldPrioritizeOverMobDefense() {
        return mainTask != null && currentPrioritizesOverMobDefense;
    }

    @Override
    public String getName() {
        return "User Tasks";
    }

    public void runTask(AltoClef mod, Task task, Runnable onFinish) {
        runTask(mod, task, onFinish, false);
    }

    public void runTask(AltoClef mod, Task task, Runnable onFinish, boolean prioritizeOverMobDefense) {
        currentOnFinish = onFinish;
        currentPrioritizesOverMobDefense = prioritizeOverMobDefense;
        Debug.logMessage("User Task Set: " + task);
        mod.getTaskRunner().enable();
        taskStopwatch.begin();
        setTask(task);
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {
        double seconds = taskStopwatch.time();
        Task oldTask = mainTask;
        mainTask = null;
        if (currentOnFinish != null) {
            currentOnFinish.run();
        }
        if (mainTask == null) {
            currentPrioritizesOverMobDefense = false;
            Debug.logMessage("User task FINISHED. Took %.3f seconds.", seconds);
            EventBus.publish(new TaskFinishedEvent(seconds, oldTask));
            mod.getTaskRunner().disable();
            mod.stopPathing();
        }
    }
}
