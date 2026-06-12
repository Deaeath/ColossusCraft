package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

public class PickupFromContainerTask extends Task {
    private final BlockPos targetContainer;
    private final ItemTarget[] targets;
    private int useCooldown;

    public PickupFromContainerTask(BlockPos targetContainer, ItemTarget... targets) {
        this.targetContainer = targetContainer;
        this.targets = targets;
    }

    @Override
    protected void onStart(AltoClef mod) {
        useCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen)) {
            if (mod.getPlayer() != null && mod.getPlayer().blockPosition().distSqr(targetContainer) > 16) {
                return new GetToBlockTask(targetContainer);
            }
            if (useCooldown-- <= 0 && mod.getController() != null && mod.getPlayer() != null) {
                useCooldown = 10;
                LookHelper.lookAt(mod, targetContainer);
                mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(targetContainer), Direction.UP, targetContainer, false));
            }
            return null;
        }
        for (Slot slot : Slot.getCurrentScreenSlots()) {
            if (Slot.isCursor(slot) || slot.isSlotInPlayerInventory()) continue;
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            if (!stack.isEmpty() && Arrays.stream(targets).anyMatch(target -> target.matches(stack.getItem()))) {
                mod.getSlotHandler().clickSlot(slot, 0, ClickType.QUICK_MOVE);
                setDebugState("Pickup from container");
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.itemTargetsMetInventory(mod, targets);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof PickupFromContainerTask task
                && task.targetContainer.equals(targetContainer)
                && Arrays.equals(task.targets, targets);
    }

    @Override
    protected String toDebugString() {
        return "Pickup from container " + targetContainer.toShortString();
    }
}
