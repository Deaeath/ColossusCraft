package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;

public class GetToBlockTask extends Task {
    private final BlockPos target;
    private final Dimension dimension;
    private int commandCooldown;

    public GetToBlockTask(BlockPos target) {
        this(target, null);
    }

    // Upstream's "preferStairs" flag isn't used by our pathing; accept it for API parity.
    public GetToBlockTask(BlockPos target, boolean preferStairs) {
        this(target, null);
    }

    public GetToBlockTask(BlockPos target, Dimension dimension) {
        this.target = target;
        this.dimension = dimension;
    }

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (dimension != null && dimension != WorldHelper.getCurrentDimension()) {
            return new DefaultGoToDimensionTask(dimension);
        }
        if (commandCooldown-- <= 0) {
            commandCooldown = 40;
            mod.runBaritone("goto " + target.getX() + " " + target.getY() + " " + target.getZ());
        }
        setDebugState("Goto " + target.toShortString());
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return mod.getPlayer() != null
                && (dimension == null || dimension == WorldHelper.getCurrentDimension())
                && mod.getPlayer().blockPosition().distSqr(target) <= 4.0;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GetToBlockTask task
                && task.target.equals(target)
                && task.dimension == dimension;
    }

    @Override
    protected String toDebugString() {
        return "Get to block " + target.toShortString();
    }
}
