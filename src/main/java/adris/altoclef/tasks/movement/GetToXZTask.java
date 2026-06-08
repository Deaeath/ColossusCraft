package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;

public class GetToXZTask extends Task {
    private final int x;
    private final int z;
    private final Dimension dimension;
    private int commandCooldown;

    public GetToXZTask(int x, int z) {
        this(x, z, null);
    }

    public GetToXZTask(int x, int z, Dimension dimension) {
        this.x = x;
        this.z = z;
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
            mod.runBaritone("goto " + x + " " + z);
        }
        setDebugState("Goto xz " + x + " " + z);
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        if (mod.getPlayer() == null || (dimension != null && dimension != WorldHelper.getCurrentDimension())) return false;
        double dx = mod.getPlayer().getX() - x;
        double dz = mod.getPlayer().getZ() - z;
        return dx * dx + dz * dz <= 9.0;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GetToXZTask task && task.x == x && task.z == z && task.dimension == dimension;
    }

    @Override
    protected String toDebugString() {
        return "Get to xz " + x + " " + z;
    }
}
