package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.construction.ConstructNetherPortalTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class DefaultGoToDimensionTask extends Task {
    private final Dimension target;
    private int commandCooldown;
    private int useCooldown;

    public DefaultGoToDimensionTask(Dimension target) {
        this.target = target;
    }

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
        useCooldown = 0;
        mod.getBlockTracker().trackBlock(Blocks.NETHER_PORTAL, Blocks.END_PORTAL);
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (isFinished(mod)) return null;
        if (target == Dimension.END) {
            setDebugState("End travel requires stronghold portal task");
            return null;
        }
        BlockPos portal = nearestPortal(mod);
        if (portal == null) {
            if (target == Dimension.NETHER && WorldHelper.getCurrentDimension() == Dimension.OVERWORLD) {
                setDebugState("Build Nether portal");
                return new ConstructNetherPortalTask();
            }
            setDebugState("No visible nether portal");
            return null;
        }
        if (mod.getPlayer() != null && mod.getPlayer().blockPosition().distSqr(portal) > 6.0) {
            if (commandCooldown-- <= 0) {
                commandCooldown = 40;
                mod.runBaritone("goto " + portal.getX() + " " + portal.getY() + " " + portal.getZ());
            }
            setDebugState("Move to portal " + portal.toShortString());
            return null;
        }
        if (useCooldown-- <= 0 && mod.getController() != null && mod.getPlayer() != null) {
            useCooldown = 20;
            LookHelper.lookAt(mod, portal);
            mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(portal), Direction.UP, portal, false));
        }
        setDebugState("Enter portal");
        return null;
    }

    private BlockPos nearestPortal(AltoClef mod) {
        return mod.getBlockTracker().getNearestWithinRange(mod.getPlayer().blockPosition(), 64, Blocks.NETHER_PORTAL).orElse(null);
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return target == WorldHelper.getCurrentDimension();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(Blocks.NETHER_PORTAL, Blocks.END_PORTAL);
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof DefaultGoToDimensionTask task && task.target == target;
    }

    @Override
    protected String toDebugString() {
        return "Go to dimension " + target;
    }
}
