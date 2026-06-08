package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class GiveItemToPlayerTask extends Task {
    private final String username;
    private final ItemTarget target;
    private int dropped;
    private int commandCooldown;
    private int dropCooldown;

    public GiveItemToPlayerTask(String username, ItemTarget target) {
        this.username = username;
        this.target = target;
    }

    @Override
    protected void onStart(AltoClef mod) {
        dropped = 0;
        commandCooldown = 0;
        dropCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        int remaining = target.getTargetCount() - dropped;
        if (remaining <= 0) return null;
        if (mod.getItemStorage().getItemCountInventoryOnly(target.getMatches()) <= 0) {
            setDebugState("Collecting " + target);
            return new CollectItemTask(new ItemTarget(target, remaining));
        }
        Player player = findPlayer();
        if (player == null) {
            setDebugState("Player not visible: " + username);
            return null;
        }
        double distSqr = player.distanceToSqr(mod.getPlayer());
        if (distSqr > 9.0) {
            if (commandCooldown-- <= 0) {
                commandCooldown = 20;
                mod.runBaritone("goto " + player.blockPosition().getX() + " " + player.blockPosition().getY() + " " + player.blockPosition().getZ());
            }
            setDebugState("Moving to " + username);
            return null;
        }
        mod.stopPathing();
        if (StorageHelper.itemTargetsMetInventory(mod, new ItemTarget(target, 1))
                && mod.getSlotHandler().forceEquipItem(target, false)
                && mod.getPlayer() != null
                && dropCooldown-- <= 0) {
            dropCooldown = 3;
            if (mod.getPlayer().drop(false)) {
                dropped++;
            }
        }
        setDebugState("Giving " + target + " to " + username + " dropped=" + dropped);
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
    public boolean isFinished(AltoClef mod) {
        return dropped >= target.getTargetCount();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GiveItemToPlayerTask task
                && task.username.equalsIgnoreCase(username)
                && task.target.equals(target);
    }

    @Override
    protected String toDebugString() {
        return "Give " + target + " to " + username;
    }
}
