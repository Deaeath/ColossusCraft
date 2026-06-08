package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class GetWithinRangeOfBlockTask extends Task {
    private final BlockPos blockPos;
    private final int range;
    private int commandCooldown;

    public GetWithinRangeOfBlockTask(BlockPos blockPos, int range) {
        this.blockPos = blockPos;
        this.range = range;
    }

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (commandCooldown-- <= 0) {
            mod.runBaritone("goto " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ());
            commandCooldown = 40;
        }
        setDebugState("Within " + range + " of " + blockPos.toShortString());
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return mod.getPlayer() != null && mod.getPlayer().blockPosition().distSqr(blockPos) <= range * range;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GetWithinRangeOfBlockTask task && task.blockPos.equals(blockPos) && task.range == range;
    }

    @Override
    protected String toDebugString() {
        return "Get within range";
    }
}
