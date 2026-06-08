package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;

public class TimeoutWanderTask extends Task {
    private final float distanceToWander;
    private final boolean forceExplore;
    private int ticks;
    private int commandCooldown;

    public TimeoutWanderTask(float distanceToWander, boolean increaseRange) {
        this.distanceToWander = distanceToWander;
        this.forceExplore = false;
    }

    public TimeoutWanderTask(float distanceToWander) {
        this(distanceToWander, false);
    }

    public TimeoutWanderTask() {
        this(Float.POSITIVE_INFINITY, false);
    }

    public TimeoutWanderTask(boolean forceExplore) {
        this.distanceToWander = Float.POSITIVE_INFINITY;
        this.forceExplore = forceExplore;
    }

    public void resetWander() {
        ticks = 0;
        commandCooldown = 0;
    }

    @Override
    protected void onStart(AltoClef mod) {
        resetWander();
    }

    @Override
    protected Task onTick(AltoClef mod) {
        ticks++;
        if (commandCooldown-- <= 0) {
            mod.runBaritone("explore");
            commandCooldown = forceExplore ? 40 : 100;
        }
        setDebugState("Wander");
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return Float.isFinite(distanceToWander) && ticks > Math.max(40, distanceToWander * 10);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof TimeoutWanderTask task && task.distanceToWander == distanceToWander && task.forceExplore == forceExplore;
    }

    @Override
    protected String toDebugString() {
        return "Wander";
    }
}
