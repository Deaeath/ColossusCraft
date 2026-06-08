package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

public class StoreInOpenContainerTask extends Task {
    private final ItemTarget[] targets;
    private boolean warnedNoContainer;

    public StoreInOpenContainerTask(ItemTarget... targets) {
        this.targets = targets == null ? new ItemTarget[0] : targets;
    }

    @Override
    protected void onStart(AltoClef mod) {
        warnedNoContainer = false;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (!(Minecraft.getInstance().screen instanceof ContainerScreen)) {
            if (!warnedNoContainer) {
                warnedNoContainer = true;
                mod.log("Open a chest/container first.");
            }
            return null;
        }
        for (Slot slot : Slot.getCurrentScreenSlots()) {
            if (!slot.isSlotInPlayerInventory() || Slot.isCursor(slot)) continue;
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            if (!stack.isEmpty() && shouldDeposit(mod, stack)) {
                setDebugState("Depositing " + ItemHelper.toResourceName(stack));
                mod.getSlotHandler().clickSlot(slot, 0, ClickType.QUICK_MOVE);
                return null;
            }
        }
        return null;
    }

    private boolean shouldDeposit(AltoClef mod, ItemStack stack) {
        if (targets.length == 0) {
            return ItemHelper.canThrowAwayStack(mod, stack);
        }
        return Arrays.stream(targets).anyMatch(target -> target.matches(stack.getItem()));
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        if (!(Minecraft.getInstance().screen instanceof ContainerScreen)) {
            return warnedNoContainer;
        }
        for (Slot slot : Slot.getCurrentScreenSlots()) {
            if (!slot.isSlotInPlayerInventory() || Slot.isCursor(slot)) continue;
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            if (!stack.isEmpty() && shouldDeposit(mod, stack)) return false;
        }
        return true;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof StoreInOpenContainerTask task && Arrays.equals(task.targets, targets);
    }

    @Override
    protected String toDebugString() {
        return "Deposit " + (targets.length == 0 ? "all" : Arrays.toString(targets));
    }
}
