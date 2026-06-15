package adris.altoclef.tasks.construction;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class DestroyBlockTask extends Task {
    private final BlockPos pos;
    private boolean startedDestroying;

    public DestroyBlockTask(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    protected void onStart(AltoClef mod) {
        startedDestroying = false;
        mod.getClientBaritone().getPathingBehavior().forceCancel();
    }

    @Override
    protected Task onTick(AltoClef mod) {
        BlockState state = mod.getWorld() == null ? null : mod.getWorld().getBlockState(pos);
        if (state != null && state.isAir()) return null;

        // Check if block is in reach (line-of-sight + distance) using Baritone's RotationUtils.
        // This prevents mining through intervening blocks.
        var reach = LookHelper.getReach(pos);
        if (reach.isPresent() && !mod.getFoodChain().needsToEat()) {
            // Block is reachable — look at it and hold CLICK_LEFT only when actually looking at it.
            if (!LookHelper.isLookingAt(mod, pos)) {
                LookHelper.lookAt(mod, reach.get());
            }
            if (LookHelper.isLookingAt(mod, pos)) {
                if (state != null) equipBestTool(mod, state);
                mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                if (!startedDestroying && mod.getController() != null) {
                    mod.getController().startDestroyBlock(pos, hitFace(mod));
                    startedDestroying = true;
                }
            }
            setDebugState("Mining " + pos.toShortString());
        } else {
            // Not in reach — path adjacent to the block.
            startedDestroying = false;
            mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
            if (!mod.getClientBaritone().getCustomGoalProcess().isActive()
                    && !mod.getClientBaritone().getPathingBehavior().isPathing()) {
                mod.getClientBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, 1));
            }
            setDebugState("Approach " + pos.toShortString());
        }
        return null;
    }

    private void equipBestTool(AltoClef mod, BlockState state) {
        Optional<Slot> best;
        if (mod.getBehaviour().shouldPreserveFortune()) {
            boolean isOre = StorageHelper.isOreBlock(mod, pos);
            best = StorageHelper.getBestToolSlot(mod, state, isOre);
        } else {
            best = StorageHelper.getBestToolSlot(mod, state);
        }
        Slot current = PlayerSlot.getEquipSlot();
        ItemStack currentStack = StorageHelper.getItemStackInSlot(current);
        if (best.isEmpty() && !(currentStack.getItem() instanceof DiggerItem)) {
            best = firstDiggerSlot();
        }
        if (best.isEmpty()) return;
        ItemStack bestStack = StorageHelper.getItemStackInSlot(best.get());
        boolean faster = bestStack.getDestroySpeed(state) > currentStack.getDestroySpeed(state);
        boolean currentNotTool = !(currentStack.getItem() instanceof DiggerItem);
        if (!best.get().equals(current) && (faster || currentNotTool)) {
            mod.getSlotHandler().forceEquipSlot(best.get());
            startedDestroying = false;
        }
    }

    private Direction hitFace(AltoClef mod) {
        if (mod.getPlayer() == null) return Direction.UP;
        Vec3 eye = mod.getPlayer().getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        return Direction.getNearest(eye.x - center.x, eye.y - center.y, eye.z - center.z);
    }

    private Optional<Slot> firstDiggerSlot() {
        for (Slot slot : Slot.getCurrentScreenSlots()) {
            if (!slot.isSlotInPlayerInventory()) continue;
            if (StorageHelper.getItemStackInSlot(slot).getItem() instanceof DiggerItem) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return mod.getWorld() != null && mod.getWorld().getBlockState(pos).isAir();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
        mod.getClientBaritone().getCustomGoalProcess().onLostControl();
        mod.stopPathing();
        if (mod.getController() != null) {
            mod.getController().stopDestroyBlock();
        }
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
