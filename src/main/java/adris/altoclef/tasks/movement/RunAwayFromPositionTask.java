package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

import java.util.Arrays;

public class RunAwayFromPositionTask extends Task {
    private final BlockPos[] dangerBlocks;
    private final double distance;
    private final Integer maintainY;
    private int commandCooldown;

    public RunAwayFromPositionTask(double distance, BlockPos... toRunAwayFrom) {
        this(distance, null, toRunAwayFrom);
    }

    public RunAwayFromPositionTask(double distance, Integer maintainY, BlockPos... toRunAwayFrom) {
        this.distance = distance;
        this.maintainY = maintainY;
        this.dangerBlocks = toRunAwayFrom;
    }

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (mod.getPlayer() == null || dangerBlocks.length == 0) return null;
        if (commandCooldown-- <= 0) {
            double ax = 0;
            double az = 0;
            for (BlockPos pos : dangerBlocks) {
                ax += mod.getPlayer().getX() - pos.getX();
                az += mod.getPlayer().getZ() - pos.getZ();
            }
            double len = Math.max(1, Math.sqrt(ax * ax + az * az));
            int x = (int) Math.round(mod.getPlayer().getX() + ax / len * distance);
            int z = (int) Math.round(mod.getPlayer().getZ() + az / len * distance);
            int y = maintainY != null ? maintainY : mod.getPlayer().blockPosition().getY();
            mod.runBaritone("goto " + x + " " + y + " " + z);
            commandCooldown = 40;
        }
        setDebugState("Run away");
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof RunAwayFromPositionTask task && Arrays.equals(task.dangerBlocks, dangerBlocks) && task.distance == distance;
    }

    @Override
    protected String toDebugString() {
        return "Run away from " + Arrays.toString(dangerBlocks);
    }
}
