package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.TaskChain;
import adris.altoclef.tasksystem.TaskRunner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;

/**
 * Automatically respawns after death instead of leaving the bot stuck on the death screen.
 */
public class DeathMenuChain extends TaskChain {

    public DeathMenuChain(TaskRunner runner) {
        super(runner);
    }

    private static boolean isDead() {
        Minecraft mc = Minecraft.getInstance();
        return (mc.player != null && !mc.player.isAlive()) || mc.screen instanceof DeathScreen;
    }

    @Override
    public boolean isActive() {
        return isDead();
    }

    @Override
    public float getPriority(AltoClef mod) {
        return isDead() ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
    }

    @Override
    protected void onTick(AltoClef mod) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.respawn();
        }
        if (mc.screen instanceof DeathScreen) {
            mc.setScreen(null);
        }
    }

    @Override
    protected void onStop(AltoClef mod) {
    }

    @Override
    public void onInterrupt(AltoClef mod, TaskChain other) {
    }

    @Override
    public String getName() {
        return "Death Menu";
    }
}
