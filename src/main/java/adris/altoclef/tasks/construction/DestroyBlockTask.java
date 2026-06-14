package adris.altoclef.tasks.construction;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class DestroyBlockTask extends Task {
    private final BlockPos pos;
    private boolean startedDestroying;

    public DestroyBlockTask(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    protected void onStart(AltoClef mod) {
        startedDestroying = false;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        BlockState state = mod.getWorld() == null ? null : mod.getWorld().getBlockState(pos);
        if (state != null && state.isAir()) return null;
        LookHelper.lookAt(mod, pos);
        Direction face = getHitFace(mod);
        if (mod.getController() != null) {
            if (!startedDestroying) {
                mod.getController().startDestroyBlock(pos, face);
                startedDestroying = true;
            }
            mod.getController().continueDestroyBlock(pos, face);
        }
        mod.getInputControls().hold(Input.CLICK_LEFT);
        setDebugState("Destroy " + pos.toShortString());
        return null;
    }

    private Direction getHitFace(AltoClef mod) {
        if (mod.getPlayer() == null) return Direction.UP;
        Vec3 eye = mod.getPlayer().getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        return Direction.getNearest(eye.x - center.x, eye.y - center.y, eye.z - center.z);
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return mod.getWorld() != null && mod.getWorld().getBlockState(pos).isAir();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
        if (mod.getController() != null) {
            mod.getController().stopDestroyBlock();
        }
        mod.getInputControls().release(Input.CLICK_LEFT);
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
