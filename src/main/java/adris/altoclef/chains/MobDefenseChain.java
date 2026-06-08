package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.entity.DefendFromHostilesTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskRunner;
import net.minecraft.world.entity.Entity;

public class MobDefenseChain extends SingleTaskChain {
    public MobDefenseChain(TaskRunner runner) {
        super(runner);
        setTask(new DefendFromHostilesTask());
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {
    }

    @Override
    protected void onStop(AltoClef mod) {
        if (mainTask != null && mainTask.isActive()) {
            mainTask.stop(mod);
        }
        mainTask = new DefendFromHostilesTask();
    }

    @Override
    public float getPriority(AltoClef mod) {
        return hasCloseHostile(mod) ? 80 : Float.NEGATIVE_INFINITY;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public String getName() {
        return "Mob Defense";
    }

    @Override
    public void onInterrupt(AltoClef mod, adris.altoclef.tasksystem.TaskChain other) {
        Task current = getCurrentTask();
        if (current != null && current.isActive()) {
            current.interrupt(mod, null);
        }
    }

    private boolean hasCloseHostile(AltoClef mod) {
        if (mod.getPlayer() == null) return false;
        double reach = 5.5;
        for (Entity hostile : mod.getEntityTracker().getHostiles()) {
            if (hostile.distanceToSqr(mod.getPlayer()) <= reach * reach) {
                return true;
            }
        }
        return false;
    }
}
