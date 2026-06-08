package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.speedrun.OpenEndPortalTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.LocateResultTracker;
import net.minecraft.core.BlockPos;

public class GoToStrongholdPortalTask extends Task {
    private final int eyesNeeded;
    private final LocateStrongholdCoordinatesTask locate = new LocateStrongholdCoordinatesTask();

    public GoToStrongholdPortalTask(int eyesNeeded) {
        this.eyesNeeded = eyesNeeded;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        BlockPos pos = LocateResultTracker.lastStructurePos().orElse(null);
        if (pos == null) {
            return locate;
        }
        if (mod.getPlayer() == null || mod.getPlayer().blockPosition().distSqr(pos) > 24 * 24) {
            return new GetToXZTask(pos.getX(), pos.getZ());
        }
        setDebugState("Open portal eyes=" + eyesNeeded);
        return new OpenEndPortalTask();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GoToStrongholdPortalTask task && task.eyesNeeded == eyesNeeded;
    }

    @Override
    protected String toDebugString() {
        return "Go to stronghold portal";
    }
}
