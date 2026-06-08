package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;

public abstract class CustomBaritoneGoalTask extends Task {
    private final boolean wander;
    protected Object _cachedGoal;

    public CustomBaritoneGoalTask(boolean wander) {
        this.wander = wander;
    }

    public CustomBaritoneGoalTask() {
        this(true);
    }

    @Override
    protected void onStart(AltoClef mod) {
        _cachedGoal = null;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (_cachedGoal == null) {
            _cachedGoal = newGoal(mod);
        }
        if (wander) {
            mod.runBaritone("explore");
        }
        setDebugState("Custom Baritone goal");
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    protected abstract Object newGoal(AltoClef mod);

    protected void onWander(AltoClef mod) {
    }
}
