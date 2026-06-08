package adris.altoclef.control;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.CursorSlot;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerReal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.EmptyMapItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SpawnEggItem;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class SlotHandler {

    private final AltoClef mod;
    private final TimerReal slotActionTimer = new TimerReal(0);
    private boolean overrideTimerOnce;

    public SlotHandler(AltoClef mod) {
        this.mod = mod;
    }

    private void forceAllowNextSlotAction() {
        overrideTimerOnce = true;
    }

    public boolean canDoSlotAction() {
        if (overrideTimerOnce) {
            overrideTimerOnce = false;
            return true;
        }
        slotActionTimer.setInterval(mod.getModSettings().getContainerItemMoveDelay());
        return slotActionTimer.elapsed();
    }

    public void registerSlotAction() {
        mod.getItemStorage().registerSlotAction();
        slotActionTimer.reset();
    }

    public void clickSlot(Slot slot, int mouseButton, ClickType type) {
        if (!canDoSlotAction()) return;
        if (slot.getWindowSlot() == Slot.CURSOR_SLOT_INDEX) {
            clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
            return;
        }
        clickWindowSlot(slot.getWindowSlot(), mouseButton, type);
    }

    private void clickSlotForce(Slot slot, int mouseButton, ClickType type) {
        forceAllowNextSlotAction();
        clickSlot(slot, mouseButton, type);
    }

    private void clickWindowSlot(int windowSlot, int mouseButton, ClickType type) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().gameMode == null) return;
        registerSlotAction();
        try {
            Minecraft.getInstance().gameMode.handleInventoryMouseClick(player.containerMenu.containerId, windowSlot, mouseButton, type, player);
        } catch (Exception e) {
            Debug.logWarning("Slot click error: " + e.getMessage());
        }
    }

    public void forceEquipItemToOffhand(Item toEquip) {
        if (StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT).getItem() == toEquip) return;
        List<Slot> itemSlots = mod.getItemStorage().getSlotsWithItemPlayerInventory(false, toEquip);
        for (Slot slot : itemSlots) {
            if (!Slot.isCursor(slot)) {
                clickSlot(slot, 0, ClickType.PICKUP);
            } else {
                clickSlot(PlayerSlot.OFFHAND_SLOT, 0, ClickType.PICKUP);
            }
        }
    }

    public boolean forceEquipItem(Item toEquip) {
        if (StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot()).getItem() == toEquip) return true;
        if (mod.getPlayer() == null) return false;
        mod.getPlayer().getInventory().selected = 1;
        boolean inCursor = StorageHelper.getItemStackInSlot(CursorSlot.SLOT).getItem() == toEquip;
        List<Slot> itemSlots = mod.getItemStorage().getSlotsWithItemScreen(toEquip);
        if (itemSlots.isEmpty()) return false;
        for (Slot slot : itemSlots) {
            clickSlotForce(Objects.requireNonNull(slot), inCursor ? 0 : 1, inCursor ? ClickType.PICKUP : ClickType.SWAP);
        }
        return true;
    }

    public boolean forceDeequipHitTool() {
        return forceDeequip(stack -> stack.getItem() instanceof DiggerItem);
    }

    public void forceDeequipRightClickableItem() {
        forceDeequip(stack -> {
            Item item = stack.getItem();
            return item instanceof BucketItem
                    || item == Items.ENDER_EYE
                    || item == Items.BOW
                    || item == Items.CROSSBOW
                    || item == Items.FLINT_AND_STEEL
                    || item == Items.FIRE_CHARGE
                    || item == Items.ENDER_PEARL
                    || item instanceof FireworkRocketItem
                    || item instanceof SpawnEggItem
                    || item == Items.END_CRYSTAL
                    || item == Items.EXPERIENCE_BOTTLE
                    || item instanceof PotionItem
                    || item == Items.TRIDENT
                    || item == Items.WRITABLE_BOOK
                    || item == Items.WRITTEN_BOOK
                    || item instanceof FishingRodItem
                    || item == Items.COMPASS
                    || item instanceof EmptyMapItem
                    || item instanceof Equipable
                    || item == Items.LEAD
                    || item == Items.SHIELD;
        });
    }

    public boolean forceDeequip(Predicate<ItemStack> isBad) {
        ItemStack equip = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot());
        ItemStack cursor = StorageHelper.getItemStackInSlot(CursorSlot.SLOT);
        if (isBad.test(cursor)) {
            Optional<Slot> fit = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(equip, false);
            if (fit.isEmpty()) {
                for (Slot slot : Slot.getCurrentScreenSlots()) {
                    if (!isBad.test(StorageHelper.getItemStackInSlot(slot))) {
                        clickSlotForce(slot, 0, ClickType.PICKUP);
                        return false;
                    }
                }
                if (ItemHelper.canThrowAwayStack(mod, cursor)) {
                    clickSlotForce(Slot.UNDEFINED, 0, ClickType.PICKUP);
                    return true;
                }
                return false;
            }
            clickSlotForce(fit.get(), 0, ClickType.PICKUP);
            return true;
        }
        if (isBad.test(equip)) {
            clickSlotForce(PlayerSlot.getEquipSlot(), 0, ClickType.PICKUP);
            return false;
        }
        if (equip.isEmpty() && !cursor.isEmpty()) {
            clickSlotForce(PlayerSlot.getEquipSlot(), 0, ClickType.PICKUP);
            return true;
        }
        return true;
    }

    public void forceEquipSlot(Slot slot) {
        Slot target = PlayerSlot.getEquipSlot();
        clickSlotForce(slot, target.getInventorySlot(), ClickType.SWAP);
    }

    public boolean forceEquipItem(Item[] matches, boolean unInterruptable) {
        return forceEquipItem(new ItemTarget(matches, 1), unInterruptable);
    }

    public boolean forceEquipItem(ItemTarget toEquip, boolean unInterruptable) {
        if (toEquip == null) return false;
        Slot target = PlayerSlot.getEquipSlot();
        if (toEquip.matches(StorageHelper.getItemStackInSlot(target).getItem())) return true;
        for (Item item : toEquip.getMatches()) {
            if (mod.getItemStorage().hasItem(item) && forceEquipItem(item)) return true;
        }
        return false;
    }

    public boolean forceEquipItem(Item... toEquip) {
        return forceEquipItem(toEquip, false);
    }

    public void refreshInventory() {
        if (Minecraft.getInstance().player == null) return;
        int size = Minecraft.getInstance().player.getInventory().items.size();
        for (int i = 0; i < size; ++i) {
            Slot slot = Slot.getFromCurrentScreenInventory(i);
            clickSlotForce(slot, 0, ClickType.PICKUP);
            clickSlotForce(slot, 0, ClickType.PICKUP);
        }
    }
}
