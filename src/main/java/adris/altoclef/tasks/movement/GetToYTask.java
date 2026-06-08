package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;

public class GetToYTask extends Task {
    private final int y;
    private final Dimension dimension;
    private int commandCooldown;

    public GetToYTask(int y) {
        this(y, null);
    }

    public GetToYTask(int y, Dimension dimension) {
        this.y = y;
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
        if (mod.getPlayer() != null && commandCooldown-- <= 0) {
            commandCooldown = 40;
            mod.runBaritone("goto " + mod.getPlayer().blockPosition().getX() + " " + y + " " + mod.getPlayer().blockPosition().getZ());
        }
        setDebugState("Goto y " + y);
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return mod.getPlayer() != null
                && (dimension == null || dimension == WorldHelper.getCurrentDimension())
                && Math.abs(mod.getPlayer().getY() - y) <= 2.0;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GetToYTask task && task.y == y && task.dimension == dimension;
    }

    @Override
    protected String toDebugString() {
        return "Get to y " + y;
    }
}
