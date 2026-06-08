package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.world.entity.Entity;

public abstract class DoToClosestEntityTask extends Task {
    protected abstract Task getGoalTask(Entity entity);

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Entity entity = mod.getEntityTracker().getHostiles().stream().findFirst().orElse(null);
        return entity == null ? null : getGoalTask(entity);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }
}
