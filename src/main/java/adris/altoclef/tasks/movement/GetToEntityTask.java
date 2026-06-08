package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.world.entity.Entity;

public class GetToEntityTask extends Task {
    private final Entity entity;
    private final double closeEnoughDistance;
    private int commandCooldown;

    public GetToEntityTask(Entity entity, double closeEnoughDistance) {
        this.entity = entity;
        this.closeEnoughDistance = closeEnoughDistance;
    }

    public GetToEntityTask(Entity entity) {
        this(entity, 1);
    }

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (entity == null || !entity.isAlive()) return null;
        if (commandCooldown-- <= 0) {
            commandCooldown = 20;
            mod.runBaritone("goto " + entity.blockPosition().getX() + " " + entity.blockPosition().getY() + " " + entity.blockPosition().getZ());
        }
        setDebugState("Go to entity");
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return mod.getPlayer() != null && entity != null && mod.getPlayer().distanceToSqr(entity) <= closeEnoughDistance * closeEnoughDistance;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GetToEntityTask task && task.entity == entity && task.closeEnoughDistance == closeEnoughDistance;
    }

    @Override
    protected String toDebugString() {
        return "Get to entity";
    }
}
