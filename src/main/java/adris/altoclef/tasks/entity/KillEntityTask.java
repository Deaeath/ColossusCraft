package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.control.KillAura;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class KillEntityTask extends Task {
    private final Entity entity;
    // If we can't damage or make approach progress, give up so wall-stuck mobs do not deadlock forever.
    private final TimerGame giveUpTimer = new TimerGame(6.0);
    private float lastHealth = Float.MAX_VALUE;
    private double lastDistanceSqr = Double.POSITIVE_INFINITY;
    private boolean gaveUp;

    public KillEntityTask(Entity entity) {
        this.entity = entity;
    }

    @Override
    protected void onStart(AltoClef mod) {
        gaveUp = false;
        lastHealth = entity instanceof LivingEntity le ? le.getHealth() : Float.MAX_VALUE;
        lastDistanceSqr = mod.getPlayer() == null ? Double.POSITIVE_INFINITY : mod.getPlayer().distanceToSqr(entity);
        giveUpTimer.reset();
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (entity == null || !entity.isAlive()) return null;

        double reachSq = mod.getModSettings().getEntityReachRange() * mod.getModSettings().getEntityReachRange();
        double distanceSqr = mod.getPlayer() == null ? Double.POSITIVE_INFINITY : mod.getPlayer().distanceToSqr(entity);
        if (distanceSqr + 0.25 < lastDistanceSqr || distanceSqr > lastDistanceSqr + 16) {
            giveUpTimer.reset();
        }
        lastDistanceSqr = distanceSqr;

        if (entity instanceof LivingEntity le && le.getHealth() < lastHealth) {
            lastHealth = le.getHealth();
            giveUpTimer.reset();
        }

        if (giveUpTimer.elapsed()) {
            mod.getEntityTracker().requestEntityUnreachable(entity);
            gaveUp = true;
            setDebugState("Gave up (unreachable)");
            return null;
        }
        if (mod.getPlayer() != null && distanceSqr > reachSq) {
            return new GetToEntityTask(entity, mod.getModSettings().getEntityReachRange());
        }
        if (mod.getPlayer() != null) {
            mod.stopPathing();
            KillAura killAura = mod.getKillAura();
            killAura.tickStart();
            killAura.applyAura(entity);
            killAura.tickEnd(mod);
        }
        setDebugState("Kill entity");
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return gaveUp || entity == null || !entity.isAlive();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.getKillAura().stopShielding(mod);
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
