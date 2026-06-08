package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

public class KillEntityTask extends Task {
    private final Entity entity;
    private int attackCooldown;

    public KillEntityTask(Entity entity) {
        this.entity = entity;
    }

    @Override
    protected void onStart(AltoClef mod) {
        attackCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (entity == null || !entity.isAlive()) return null;
        if (mod.getPlayer() != null && mod.getPlayer().distanceToSqr(entity) > mod.getModSettings().getEntityReachRange() * mod.getModSettings().getEntityReachRange()) {
            return new GetToEntityTask(entity, mod.getModSettings().getEntityReachRange());
        }
        if (mod.getPlayer() != null && Minecraft.getInstance().gameMode != null && attackCooldown-- <= 0) {
            attackCooldown = 10;
            LookHelper.lookAt(mod, entity.getEyePosition());
            Minecraft.getInstance().gameMode.attack(mod.getPlayer(), entity);
            mod.getPlayer().swing(InteractionHand.MAIN_HAND);
        }
        setDebugState("Kill entity");
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return entity == null || !entity.isAlive();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof KillEntityTask task && task.entity == entity;
    }

    @Override
    protected String toDebugString() {
        return "Kill entity";
    }
}
