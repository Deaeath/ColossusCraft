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

    @Override
    protected Task onTick(AltoClef mod) {
        Entity target = nearest(mod);
        if (target == null) {
            setDebugState("No entity");
            return null;
        }
        return new KillEntityTask(target);
    }

    private Entity nearest(AltoClef mod) {
        if (Minecraft.getInstance().level == null || mod.getPlayer() == null) return null;
        Entity best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (entity == null || !entity.isAlive() || !accept.test(entity)) continue;
            if (entityClasses.length != 0 && Arrays.stream(entityClasses).noneMatch(type -> type.isInstance(entity))) continue;
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
