package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public abstract class AbstractDoToClosestObjectTask<T> extends Task {
    protected abstract Vec3 getPos(AltoClef mod, T obj);

    protected abstract Optional<T> getClosestTo(AltoClef mod, Vec3 pos);

    protected abstract Vec3 getOriginPos(AltoClef mod);

    protected abstract Task getGoalTask(T obj);

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Optional<T> closest = getClosestTo(mod, getOriginPos(mod));
        return closest.map(this::getGoalTask).orElse(null);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }
}
