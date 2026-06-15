package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasksystem.TaskChain;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class PlayerInteractionFixChain extends TaskChain {
    private final TimerGame _stackHeldTimeout = new TimerGame(1);
    private final TimerGame _generalDuctTapeSwapTimeout = new TimerGame(30);
    private final TimerGame _shiftDepressTimeout = new TimerGame(10);
    private final TimerGame _mouseMovingButScreenOpenTimeout = new TimerGame(1);
    private ItemStack _lastHandStack = null;

    private Screen _lastScreen;
    private Rotation _lastLookRotation;

    public PlayerInteractionFixChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    protected void onStop(AltoClef mod) {
    }

    @Override
    public void onInterrupt(AltoClef mod, TaskChain other) {
    }

    @Override
    protected void onTick(AltoClef mod) {
    }

    @Override
    public float getPriority(AltoClef mod) {

        if (!AltoClef.inGame()) return Float.NEGATIVE_INFINITY;

        // Fortune-preserve: if we're breaking a non-ore block while a fortune tool is equipped,
        // swap to the best non-fortune tool. This catches Baritone's own mining loop which
        // bypasses DestroyBlockTask's equipBestTool entirely.
        if (mod.getBehaviour().shouldPreserveFortune() && mod.getControllerExtras().isBreakingBlock()) {
            net.minecraft.core.BlockPos breakPos = mod.getControllerExtras().getBreakingBlockPos();
            if (breakPos != null && !StorageHelper.isOreBlock(mod, breakPos)) {
                ItemStack equipped = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot());
                if (StorageHelper.hasFortuneEnchantment(equipped)) {
                    // Currently using fortune on a non-ore — find a non-fortune replacement.
                    net.minecraft.world.level.block.state.BlockState bState =
                            mod.getWorld() != null ? mod.getWorld().getBlockState(breakPos) : null;
                    adris.altoclef.util.slots.Slot replacement = null;
                    if (bState != null) {
                        java.util.Optional<adris.altoclef.util.slots.Slot> best =
                                StorageHelper.getBestToolSlot(mod, bState, false);
                        if (best.isPresent() && !StorageHelper.hasFortuneEnchantment(
                                StorageHelper.getItemStackInSlot(best.get()))) {
                            replacement = best.get();
                        }
                    }
                    // Fall back: any non-fortune digger in inventory
                    if (replacement == null) {
                        for (adris.altoclef.util.slots.Slot slot : adris.altoclef.util.slots.Slot.getCurrentScreenSlots()) {
                            if (!slot.isSlotInPlayerInventory()) continue;
                            ItemStack s = StorageHelper.getItemStackInSlot(slot);
                            if (s.getItem() instanceof net.minecraft.world.item.DiggerItem
                                    && !StorageHelper.hasFortuneEnchantment(s)) {
                                replacement = slot;
                                break;
                            }
                        }
                    }
                    if (replacement != null) {
                        mod.getSlotHandler().forceEquipSlot(replacement);
                    }
                }
            }
        }

        // Unpress shift (it gets stuck for some reason???)
        if (mod.getInputControls().isHeldDown(Input.SNEAK)) {
            if (_shiftDepressTimeout.elapsed()) {
                mod.getInputControls().release(Input.SNEAK);
            }
        } else {
            _shiftDepressTimeout.reset();
        }

        // Refresh inventory
        if (_generalDuctTapeSwapTimeout.elapsed()) {
            if (!mod.getControllerExtras().isBreakingBlock()) {
                Debug.logMessage("Refreshed inventory...");
                mod.getSlotHandler().refreshInventory();
                _generalDuctTapeSwapTimeout.reset();
                return Float.NEGATIVE_INFINITY;
            }
        }

        ItemStack currentStack = StorageHelper.getItemStackInCursorSlot();

        if (currentStack != null && !currentStack.isEmpty()) {
            if (_lastHandStack == null || !ItemStack.matches(currentStack, _lastHandStack)) {
                // We're holding a new item in our stack!
                _stackHeldTimeout.reset();
                _lastHandStack = currentStack.copy();
            }
        } else {
            _stackHeldTimeout.reset();
            _lastHandStack = null;
        }

        // If we have something in our hand for a period of time...
        if (_lastHandStack != null && _stackHeldTimeout.elapsed()) {
            Optional<Slot> moveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(_lastHandStack, false);
            if (moveTo.isPresent()) {
                mod.getSlotHandler().clickSlot(moveTo.get(), 0, ClickType.PICKUP);
                return Float.NEGATIVE_INFINITY;
            }
            if (ItemHelper.canThrowAwayStack(mod, StorageHelper.getItemStackInCursorSlot())) {
                mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
                return Float.NEGATIVE_INFINITY;
            }
            Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
            if (garbage.isPresent()) {
                mod.getSlotHandler().clickSlot(garbage.get(), 0, ClickType.PICKUP);
                return Float.NEGATIVE_INFINITY;
            }
            mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
            return Float.NEGATIVE_INFINITY;
        }

        if (shouldCloseOpenScreen(mod)) {
            ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot();
            if (!cursorStack.isEmpty()) {
                Optional<Slot> moveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursorStack, false);
                if (moveTo.isPresent()) {
                    mod.getSlotHandler().clickSlot(moveTo.get(), 0, ClickType.PICKUP);
                    return Float.NEGATIVE_INFINITY;
                }
                if (ItemHelper.canThrowAwayStack(mod, cursorStack)) {
                    mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
                    return Float.NEGATIVE_INFINITY;
                }
                Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
                if (garbage.isPresent()) {
                    mod.getSlotHandler().clickSlot(garbage.get(), 0, ClickType.PICKUP);
                    return Float.NEGATIVE_INFINITY;
                }
                mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
            } else {
                StorageHelper.closeScreen();
            }
            return Float.NEGATIVE_INFINITY;
        }

        return Float.NEGATIVE_INFINITY;
    }

    private boolean shouldCloseOpenScreen(AltoClef mod) {
        if (!mod.getModSettings().shouldCloseScreenWhenLookingOrMining())
            return false;
        // Only check look if we've had the same screen open for a while
        Screen openScreen = Minecraft.getInstance().screen;
        if (openScreen != _lastScreen) {
            _mouseMovingButScreenOpenTimeout.reset();
        }
        // We're in a screen we DON'T want to cancel out of
        // Never auto-close the player's own inventory/menus — let the user open them while the bot plays.
        if (openScreen == null || openScreen instanceof ChatScreen || openScreen instanceof PauseScreen
                || openScreen instanceof DeathScreen
                || openScreen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                || openScreen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
            _mouseMovingButScreenOpenTimeout.reset();
            return false;
        }
        // Check for rotation change
        Rotation look = LookHelper.getLookRotation();
        if (_lastLookRotation != null && _mouseMovingButScreenOpenTimeout.elapsed()) {
            Rotation delta = look.subtract(_lastLookRotation);
            if (Math.abs(delta.getYaw()) > 0.1f || Math.abs(delta.getPitch()) > 0.1f) {
                _lastLookRotation = look;
                return true;
            }
        } else {
            _lastLookRotation = look;
        }
        _lastScreen = openScreen;
        return false;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public String getName() {
        return "Hand Stack Fix Chain";
    }

    @Override
    public boolean isPassive() {
        return true;
    }
}
