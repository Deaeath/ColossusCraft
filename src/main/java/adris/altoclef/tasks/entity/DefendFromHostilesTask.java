package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

public class DefendFromHostilesTask extends Task {
    private int attackCooldown;

    @Override
    protected void onStart(AltoClef mod) {
        attackCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Entity target = nearestHostile(mod);
        if (target == null) return null;
        mod.stopPathing();
        LookHelper.lookAt(mod, target.getEyePosition());
        if (attackCooldown-- <= 0 && Minecraft.getInstance().gameMode != null && mod.getPlayer() != null) {
            attackCooldown = 10;
            Minecraft.getInstance().gameMode.attack(mod.getPlayer(), target);
            mod.getPlayer().swing(InteractionHand.MAIN_HAND);
        }
        setDebugState("Defend from " + target.getType().toShortString());
        return null;
    }

    private Entity nearestHostile(AltoClef mod) {
        if (mod.getPlayer() == null) return null;
        Entity best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Entity hostile : mod.getEntityTracker().getHostiles()) {
            double dist = hostile.distanceToSqr(mod.getPlayer());
            if (dist < bestDist && dist <= 5.5 * 5.5) {
                best = hostile;
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
        return other instanceof DefendFromHostilesTask;
    }

    @Override
    protected String toDebugString() {
        return "Defend from hostiles";
    }
}
