package adris.altoclef.tasks.construction;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import net.minecraft.core.BlockPos;

public class DestroyBlockTask extends Task {
    private final BlockPos pos;
    private int commandCooldown;

    public DestroyBlockTask(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (mod.getWorld() != null && mod.getWorld().getBlockState(pos).isAir()) return null;
        if (mod.getPlayer() != null && mod.getPlayer().blockPosition().distSqr(pos) > 25) {
            return new GetToBlockTask(pos);
        }
        if (commandCooldown-- <= 0) {
            commandCooldown = 40;
            mod.runBaritone("mine " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        }
        LookHelper.lookAt(mod, pos);
        setDebugState("Destroy " + pos.toShortString());
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return mod.getWorld() != null && mod.getWorld().getBlockState(pos).isAir();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof DestroyBlockTask task && task.pos.equals(pos);
    }

    @Override
    protected String toDebugString() {
        return "Destroy block";
    }
}
