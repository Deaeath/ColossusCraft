package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public abstract class AbstractDoToEntityTask extends Task {
    private final double maintainDistance;
    private final double combatGuardLowerRange;
    private final double combatGuardLowerFieldRadius;
    private Entity current;

    protected AbstractDoToEntityTask(double maintainDistance, double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
        this.maintainDistance = maintainDistance;
        this.combatGuardLowerRange = combatGuardLowerRange;
        this.combatGuardLowerFieldRadius = combatGuardLowerFieldRadius;
    }

    protected AbstractDoToEntityTask(double maintainDistance) {
        this(maintainDistance, -1, -1);
    }

    protected AbstractDoToEntityTask(double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
        this(0, combatGuardLowerRange, combatGuardLowerFieldRadius);
    }

    @Override
    protected void onStart(AltoClef mod) {
        current = null;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Optional<Entity> target = getEntityTarget(mod);
        if (target.isEmpty() || !target.get().isAlive()) {
            current = null;
            setDebugState("Wandering");
            return new TimeoutWanderTask();
        }

        current = target.get();
        if (mod.getPlayer() != null && mod.getPlayer().distanceToSqr(current) > maintainDistance * maintainDistance) {
            setDebugState("Moving to entity");
            return new GetToEntityTask(current, maintainDistance);
        }

        return onEntityInteract(mod, current);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        current = null;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof AbstractDoToEntityTask task && isSubEqual(task);
    }

    protected abstract boolean isSubEqual(AbstractDoToEntityTask other);

    protected abstract Task onEntityInteract(AltoClef mod, Entity entity);

    protected abstract Optional<Entity> getEntityTarget(AltoClef mod);
}
