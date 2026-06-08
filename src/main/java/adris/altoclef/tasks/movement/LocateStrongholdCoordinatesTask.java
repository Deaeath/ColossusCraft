package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.LocateResultTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Optional;

public class LocateStrongholdCoordinatesTask extends Task {
    private int commandCooldown;

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
        LocateResultTracker.clear();
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (commandCooldown-- <= 0) {
            if (Minecraft.getInstance().getConnection() != null) {
                Minecraft.getInstance().getConnection().sendCommand("locate structure minecraft:stronghold");
            }
            commandCooldown = 100;
        }
        setDebugState("Locate stronghold");
        return null;
    }

    public Optional<BlockPos> getStrongholdPos() {
        return LocateResultTracker.lastStructurePos();
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return LocateResultTracker.lastStructurePos().isPresent();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof LocateStrongholdCoordinatesTask;
    }

    @Override
    protected String toDebugString() {
        return "Locate stronghold";
    }
}
