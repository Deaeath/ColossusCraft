package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class FollowPlayerTask extends Task {
    private final String username;
    private int commandCooldown;

    public FollowPlayerTask(String username) {
        this.username = username;
    }

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Player target = findPlayer();
        if (target == null) {
            setDebugState("Player not visible: " + username);
            return null;
        }
        if (commandCooldown-- <= 0) {
            commandCooldown = 40;
            mod.runBaritone("goto " + target.blockPosition().getX() + " " + target.blockPosition().getY() + " " + target.blockPosition().getZ());
        }
        setDebugState("Following " + username + " dist=" + Math.round(Math.sqrt(target.distanceToSqr(mod.getPlayer()))));
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
        return other instanceof FollowPlayerTask task && task.username.equalsIgnoreCase(username);
    }

    @Override
    protected String toDebugString() {
        return "Follow " + username;
    }
}
