package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public class KillPlayerTask extends Task {
    private final String username;
    private int commandCooldown;
    private int attackCooldown;

    public KillPlayerTask(String username) {
        this.username = username;
    }

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
        attackCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Player target = findPlayer();
        if (target == null) {
            setDebugState("Player not visible: " + username);
            return null;
        }
        double distSqr = target.distanceToSqr(mod.getPlayer());
        if (distSqr > 9.0 && commandCooldown-- <= 0) {
            commandCooldown = 20;
            mod.runBaritone("goto " + target.blockPosition().getX() + " " + target.blockPosition().getY() + " " + target.blockPosition().getZ());
        }
        if (distSqr <= mod.getModSettings().getEntityReachRange() * mod.getModSettings().getEntityReachRange()) {
            mod.stopPathing();
            LookHelper.lookAt(mod, target.getEyePosition());
            if (attackCooldown-- <= 0 && Minecraft.getInstance().gameMode != null && mod.getPlayer() != null) {
                attackCooldown = 10;
                Minecraft.getInstance().gameMode.attack(mod.getPlayer(), target);
                mod.getPlayer().swing(InteractionHand.MAIN_HAND);
            }
        }
        setDebugState("Punking " + username + " dist=" + Math.round(Math.sqrt(distSqr)));
        return null;
    }

    private Player findPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (Player player : mc.level.players()) {
            if (player.getName().getString().equalsIgnoreCase(username)) {
                return player;
            }
        }
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return false;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof KillPlayerTask task && task.username.equalsIgnoreCase(username);
    }

    @Override
    protected String toDebugString() {
        return "Punk " + username;
    }
}
