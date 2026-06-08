package adris.altoclef.trackers.storage;

import adris.altoclef.trackers.Tracker;
import adris.altoclef.trackers.TrackerManager;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.CraftingTableSlot;
import adris.altoclef.util.slots.CursorSlot;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventorySubTracker extends Tracker {

    private final Map<Item, List<Slot>> itemToSlotPlayer = new HashMap<>();
    private final Map<Item, List<Slot>> itemToSlotContainer = new HashMap<>();
    private final Map<Item, Integer> itemCountsPlayer = new HashMap<>();
    private final Map<Item, Integer> itemCountsContainer = new HashMap<>();
    private AbstractContainerMenu previousMenu;

    public InventorySubTracker(TrackerManager manager) {
        super(manager);
    }

    private static boolean shouldIgnoreSlotForContainer(Slot slot) {
        if (slot instanceof CraftingTableSlot && slot.equals(CraftingTableSlot.OUTPUT_SLOT)) {
            return true;
        }
        if (slot instanceof PlayerSlot) {
            int window = slot.getWindowSlot();
            return window < 9 || window > 44;
        }
        return false;
    }

    public int getItemCount(boolean playerInventory, boolean containerInventory, Item... items) {
        ensureUpdated();
        int result = 0;
        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
        for (Item item : items) {
            if (playerInventory && cursor.getItem() == item) {
                result += cursor.getCount();
            }
            if (playerInventory) {
                result += itemCountsPlayer.getOrDefault(item, 0);
            }
            if (containerInventory) {
                result += itemCountsContainer.getOrDefault(item, 0);
            }
        }
        return result;
    }

    public boolean hasItem(boolean playerInventoryOnly, Item... items) {
        ensureUpdated();
        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
        for (Item item : items) {
            if (cursor.getItem() == item) return true;
            if (itemCountsPlayer.containsKey(item)) return true;
            if (!playerInventoryOnly && itemCountsContainer.containsKey(item)) return true;
        }
        return false;
    }

    public List<Slot> getSlotsWithItems(boolean playerInventory, boolean containerInventory, Item... items) {
        ensureUpdated();
        List<Slot> result = new ArrayList<>();
        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
        for (Item item : items) {
            if (playerInventory && cursor.getItem() == item) result.add(CursorSlot.SLOT);
            if (playerInventory) result.addAll(itemToSlotPlayer.getOrDefault(item, Collections.emptyList()));
            if (containerInventory) result.addAll(itemToSlotContainer.getOrDefault(item, Collections.emptyList()));
        }
        return result;
    }

    public List<ItemStack> getInventoryStacks(boolean includeCursor) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return Collections.emptyList();
        Inventory inv = player.getInventory();
        List<ItemStack> result = new ArrayList<>(41 + (includeCursor ? 1 : 0));
        if (includeCursor) {
            result.add(StorageHelper.getItemStackInCursorSlot());
        }
        result.addAll(inv.items);
        result.addAll(inv.armor);
        result.addAll(inv.offhand);
        return result;
    }

    private List<Slot> getSlotsThatCanFit(Map<Item, List<Slot>> list, ItemStack item, boolean acceptPartial) {
        List<Slot> result = new ArrayList<>();
        for (Slot slot : list.getOrDefault(item.getItem(), Collections.emptyList())) {
            if (Slot.isCursor(slot)) continue;
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            if (!stack.isEmpty() && ItemHelper.canStackTogether(item, stack)) {
                int roomLeft = stack.getMaxStackSize() - stack.getCount();
                if (acceptPartial || roomLeft >= item.getCount()) {
                    result.add(slot);
                }
            }
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            AbstractContainerMenu menu = player.containerMenu;
            for (Slot empty : list.getOrDefault(Items.AIR, Collections.emptyList())) {
                if (Slot.isCursor(empty)) continue;
                int window = empty.getWindowSlot();
                if (window >= 0 && window < menu.slots.size() && menu.getSlot(window).mayPlace(item)) {
                    result.add(empty);
                }
            }
        }
        return result;
    }

    public List<Slot> getSlotsThatCanFit(boolean includePlayer, boolean includeContainer, ItemStack item, boolean acceptPartial) {
        ensureUpdated();
        List<Slot> result = new ArrayList<>();
        if (includePlayer) result.addAll(getSlotsThatCanFit(itemToSlotPlayer, item, acceptPartial));
        if (includeContainer) result.addAll(getSlotsThatCanFit(itemToSlotContainer, item, acceptPartial));
        return result;
    }

    public boolean hasEmptySlot(boolean playerInventoryOnly) {
        return hasItem(playerInventoryOnly, Items.AIR);
    }

    private void registerItem(ItemStack stack, Slot slot, boolean playerInventory) {
        Item item = stack.isEmpty() ? Items.AIR : stack.getItem();
        int count = stack.isEmpty() ? 0 : stack.getCount();
        Map<Item, Integer> counts = playerInventory ? itemCountsPlayer : itemCountsContainer;
        Map<Item, List<Slot>> slots = playerInventory ? itemToSlotPlayer : itemToSlotContainer;
        counts.put(item, counts.getOrDefault(item, 0) + count);
        slots.computeIfAbsent(item, ignored -> new ArrayList<>()).add(slot);
    }

    @Override
    protected void updateState() {
        LocalPlayer player = Minecraft.getInstance().player;
        previousMenu = player != null ? player.containerMenu : null;
        itemToSlotPlayer.clear();
        itemToSlotContainer.clear();
        itemCountsPlayer.clear();
        itemCountsContainer.clear();
        if (player == null || previousMenu == null) return;
        for (Slot slot : Slot.getCurrentScreenSlots()) {
            if (Slot.isCursor(slot)) continue;
            if (shouldIgnoreSlotForContainer(slot)) continue;
            registerItem(StorageHelper.getItemStackInSlot(slot), slot, slot.isSlotInPlayerInventory());
        }
    }

    @Override
    protected void reset() {
        itemToSlotPlayer.clear();
        itemToSlotContainer.clear();
        itemCountsPlayer.clear();
        itemCountsContainer.clear();
    }

    @Override
    protected boolean isDirty() {
        LocalPlayer player = Minecraft.getInstance().player;
        AbstractContainerMenu menu = player != null ? player.containerMenu : null;
        return super.isDirty() || menu != previousMenu;
    }
}
