package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class OpenEndPortalTask extends Task {
    private int commandCooldown;
    private int useCooldown;

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
        useCooldown = 0;
        mod.getBlockTracker().trackBlock(Blocks.END_PORTAL_FRAME, Blocks.END_PORTAL);
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null || mod.getController() == null) return null;
        BlockPos portal = mod.getBlockTracker().getNearestWithinRange(mod.getPlayer().blockPosition(), 32, Blocks.END_PORTAL).orElse(null);
        if (portal != null) {
            if (mod.getPlayer().blockPosition().distSqr(portal) > 3.0) {
                gotoPos(mod, portal, "Enter End portal");
            } else {
                setDebugState("Standing in End portal");
            }
            return null;
        }
        List<BlockPos> frames = mod.getBlockTracker().getKnownLocations(Blocks.END_PORTAL_FRAME);
        if (frames.isEmpty()) {
            if (commandCooldown-- <= 0) {
                commandCooldown = 100;
                mod.runBaritone("explore");
            }
            setDebugState("Searching stronghold portal room");
            return null;
        }
        frames.sort(Comparator.comparingDouble(pos -> pos.distSqr(mod.getPlayer().blockPosition())));
        for (BlockPos frame : frames) {
            BlockState state = mod.getWorld().getBlockState(frame);
            if (state.is(Blocks.END_PORTAL_FRAME) && !state.getValue(EndPortalFrameBlock.HAS_EYE)) {
                if (!mod.getItemStorage().hasItemInventoryOnly(Items.ENDER_EYE)) {
                    setDebugState("Missing eye of ender");
                    return null;
                }
                if (mod.getPlayer().blockPosition().distSqr(frame) > 16.0) {
                    gotoPos(mod, frame, "Move to portal frame");
                    return null;
                }
                if (mod.getSlotHandler().forceEquipItem(Items.ENDER_EYE) && useCooldown-- <= 0) {
                    useCooldown = 8;
                    LookHelper.lookAt(mod, frame);
                    mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(frame), Direction.UP, frame, false));
                }
                setDebugState("Place eye in frame");
                return null;
            }
        }
        setDebugState("All visible frames filled; waiting portal");
        return null;
    }

    private void gotoPos(AltoClef mod, BlockPos pos, String state) {
        if (commandCooldown-- <= 0) {
            commandCooldown = 30;
            mod.runBaritone("goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        }
        setDebugState(state + " " + pos.toShortString());
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(Blocks.END_PORTAL_FRAME, Blocks.END_PORTAL);
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof OpenEndPortalTask;
    }

    @Override
    protected String toDebugString() {
        return "Open End portal";
    }
}
