package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.LocateResultTracker;
import net.minecraft.client.Minecraft;

public class LocateDesertTempleTask extends Task {
    private int cooldown;

    @Override
    protected void onStart(AltoClef mod) {
        cooldown = 0;
        LocateResultTracker.clear();
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (cooldown-- <= 0 && Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().sendCommand("locate structure minecraft:desert_pyramid");
            cooldown = 100;
        }
        return LocateResultTracker.lastStructurePos().map(pos -> (Task) new GetToXZTask(pos.getX(), pos.getZ())).orElse(null);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof LocateDesertTempleTask;
    }

    @Override
    protected String toDebugString() {
        return "Locate desert temple";
    }
}
