package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.RecipeTarget;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.CraftingTableSlot;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class CraftGenericWithRecipeBooksTask extends Task {

    private final RecipeTarget _target;
    private int _fillDelay = 0;
    private boolean _sentPacket = false;
    private boolean _ingredientsExhausted = false;

    public CraftGenericWithRecipeBooksTask(RecipeTarget target) {
        _target = target;
    }

    @Override
    protected void onStart(AltoClef mod) {
        _fillDelay = 0;
        _sentPacket = false;
        _ingredientsExhausted = false;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || mc.gameMode == null) return null;

        boolean bigCrafting = StorageHelper.isBigCraftingOpen();
        boolean inventoryOpen = StorageHelper.isPlayerInventoryOpen();

        // Move any item stuck in cursor slot to inventory before doing anything else.
        ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot();
        if (!cursorStack.isEmpty()) {
            Optional<Slot> moveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursorStack, false);
            if (moveTo.isPresent()) {
                mod.getSlotHandler().clickSlot(moveTo.get(), 0, ClickType.PICKUP);
                return null;
            }
            if (ItemHelper.canThrowAwayStack(mod, cursorStack)) {
                mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
                return null;
            }
            Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
            if (garbage.isPresent()) {
                mod.getSlotHandler().clickSlot(garbage.get(), 0, ClickType.PICKUP);
                return null;
            }
            mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
            return null;
        }

        if (!bigCrafting && !inventoryOpen) return null;

        // Collect crafted output from result slot if present.
        Slot outputSlot = bigCrafting ? CraftingTableSlot.OUTPUT_SLOT : PlayerSlot.CRAFT_OUTPUT_SLOT;
        ItemStack output = StorageHelper.getItemStackInSlot(outputSlot);
        if (!output.isEmpty() && _target.getOutputItem() == output.getItem()) {
            int have = mod.getItemStorage().getItemCount(_target.getOutputItem());
            if (have < _target.getTargetCount()) {
                setDebugState("Collecting crafted output");
                mod.getSlotHandler().clickSlot(outputSlot, 0, ClickType.QUICK_MOVE);
                _sentPacket = false; // allow refilling grid after collection
                return null;
            }
        }

        // Check if we already have enough.
        if (mod.getItemStorage().getItemCount(_target.getOutputItem()) >= _target.getTargetCount()) {
            return null;
        }

        // Wait for server to process the last fill packet.
        if (_fillDelay > 0) {
            _fillDelay--;
            return null;
        }

        // If we already sent a packet and output is still empty, check if we have
        // any ingredients left. If not, we've exhausted the materials — stop here
        // rather than looping forever (e.g. bread recipe with count=99999999).
        if (_sentPacket) {
            boolean hasIngredients = false;
            adris.altoclef.util.CraftingRecipe recipe = _target.getRecipe();
            if (recipe != null) {
                for (int i = 0; i < recipe.getSlotCount(); i++) {
                    adris.altoclef.util.ItemTarget slot = recipe.getSlot(i);
                    if (slot != null && !slot.isEmpty() && mod.getItemStorage().getItemCountInventoryOnly(slot.getMatches()) > 0) {
                        hasIngredients = true;
                        break;
                    }
                }
            }
            if (!hasIngredients) {
                _ingredientsExhausted = true;
                setDebugState("No ingredients left, done crafting");
                return null;
            }
        }

        // Use ingredient-matching recipe lookup to avoid picking wrong modpack recipe.
        setDebugState("Filling recipe for " + _target.getOutputItem());
        StorageHelper.instantFillRecipeViaBook(mod, _target.getRecipe(), _target.getOutputItem(), true);
        _fillDelay = 8;
        _sentPacket = true;
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        // Move anything left in cursor to inventory on exit.
        ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot();
        if (!cursorStack.isEmpty()) {
            Optional<Slot> moveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursorStack, false);
            moveTo.ifPresent(slot -> mod.getSlotHandler().clickSlot(slot, 0, ClickType.PICKUP));
        }
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return _ingredientsExhausted
                || mod.getItemStorage().getItemCount(_target.getOutputItem()) >= _target.getTargetCount();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CraftGenericWithRecipeBooksTask task && task._target.equals(_target);
    }

    @Override
    protected String toDebugString() {
        return "Craft (recipe-book): " + _target;
    }
}
