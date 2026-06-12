package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;
import java.util.function.Predicate;

public class KillEntitiesTask extends Task {
    private final Predicate<Entity> accept;
    private final Class<? extends Entity>[] entityClasses;

    // Lock onto one target until it's dead/gone, so we don't flip-flop between two nearby mobs
    // (which would restart the attack every tick = analysis paralysis).
    private Entity _target;
    private KillEntityTask _killTask;

    @SafeVarargs
    public KillEntitiesTask(Class<? extends Entity>... entityClasses) {
        this(entity -> true, entityClasses);
    }

    @SafeVarargs
    public KillEntitiesTask(Predicate<Entity> accept, Class<? extends Entity>... entityClasses) {
        this.accept = accept;
        this.entityClasses = entityClasses;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    private boolean valid(AltoClef mod, Entity e) {
        if (e == null || !e.isAlive() || e.isRemoved() || !accept.test(e)) return false;
        if (!mod.getEntityTracker().isEntityReachable(e)) return false; // gave up on this one
        return entityClasses.length == 0 || Arrays.stream(entityClasses).anyMatch(type -> type.isInstance(e));
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (!valid(mod, _target)) {
            _target = nearest(mod);
            _killTask = _target == null ? null : new KillEntityTask(_target);
        }
        if (_target == null) {
            setDebugState("No entity");
            return null;
        }
        setDebugState("Killing " + _target.getType().getDescriptionId());
        return _killTask;
    }

    private Entity nearest(AltoClef mod) {
        if (Minecraft.getInstance().level == null || mod.getPlayer() == null) return null;
        Entity best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (!valid(mod, entity)) continue;
            double dist = entity.distanceToSqr(mod.getPlayer());
            if (dist < bestDist) {
                best = entity;
                bestDist = dist;
            }
        }
        return best;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof KillEntitiesTask task && Arrays.equals(task.entityClasses, entityClasses);
    }

    @Override
    protected String toDebugString() {
        return "Kill entities";
    }
}
