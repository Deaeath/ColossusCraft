package adris.altoclef.trackers.storage;

import adris.altoclef.AltoClef;
import adris.altoclef.trackers.Tracker;
import adris.altoclef.trackers.TrackerManager;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.CraftingTableSlot;
import adris.altoclef.util.slots.FurnaceSlot;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ItemStorageTracker extends Tracker {

    private final InventorySubTracker inventory;
    private final ContainerSubTracker containers;

    public ItemStorageTracker(AltoClef mod, TrackerManager manager, Consumer<ContainerSubTracker> containerTrackerConsumer) {
        super(manager);
        inventory = new InventorySubTracker(manager);
        containers = new ContainerSubTracker(manager);
        containerTrackerConsumer.accept(containers);
    }

    private static Slot[] getCurrentConversionSlots() {
        if (StorageHelper.isPlayerInventoryOpen()) {
            return PlayerSlot.CRAFT_INPUT_SLOTS;
        }
        if (StorageHelper.isBigCraftingOpen()) {
            return CraftingTableSlot.INPUT_SLOTS;
        }
        if (StorageHelper.isFurnaceOpen()) {
            return new Slot[]{FurnaceSlot.INPUT_SLOT_FUEL, FurnaceSlot.INPUT_SLOT_MATERIALS};
        }
        return new Slot[0];
    }

    public int getItemCount(Item... items) {
        int conversion = Arrays.stream(getCurrentConversionSlots()).mapToInt(slot -> {
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            for (Item item : items) {
                if (stack.getItem() == item) return stack.getCount();
            }
            return 0;
        }).sum();
        return inventory.getItemCount(true, false, items) + conversion;
    }

    public int getItemCount(ItemTarget... targets) {
        return Arrays.stream(targets).mapToInt(target -> getItemCount(target.getMatches())).sum();
    }

    public int getItemCountScreen(Item... items) {
        return inventory.getItemCount(true, true, items);
    }

    public int getItemCountInventoryOnly(Item... items) {
        return inventory.getItemCount(true, false, items);
    }

    public int getItemCountContainer(Item... items) {
        return inventory.getItemCount(false, true, items);
    }

    public boolean hasItem(Item... items) {
        return Arrays.stream(getCurrentConversionSlots()).anyMatch(slot -> {
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            for (Item item : items) {
                if (stack.getItem() == item) return true;
            }
            return false;
        }) || inventory.hasItem(true, items);
    }

    public boolean hasItemInOffhand(Item item) {
        return StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT).getItem() == item;
    }

    public boolean hasItemAll(Item... items) {
        return Arrays.stream(items).allMatch(this::hasItem);
    }

    public boolean hasItem(ItemTarget... targets) {
        return Arrays.stream(targets).anyMatch(target -> hasItem(target.getMatches()));
    }

    public boolean hasItemScreen(Item... items) {
        return inventory.hasItem(false, items);
    }

    public boolean hasItemInventoryOnly(Item... items) {
        return inventory.hasItem(true, items);
    }

    public List<Slot> getSlotsWithItemScreen(Item... items) {
        return inventory.getSlotsWithItems(true, true, items);
    }

    public List<Slot> getSlotsWithItemContainer(Item... items) {
        return inventory.getSlotsWithItems(false, true, items);
    }

    public List<Slot> getSlotsWithItemPlayerInventory(boolean includeCraftArmorOffhand, Item... items) {
        List<Slot> results = inventory.getSlotsWithItems(true, false, items);
        if (includeCraftArmorOffhand) {
            HashSet<Item> toCheck = new HashSet<>(Arrays.asList(items));
            for (Slot other : StorageHelper.INACCESSIBLE_PLAYER_SLOTS) {
                if (toCheck.contains(StorageHelper.getItemStackInSlot(other).getItem())) {
                    results.add(other);
                }
            }
        }
        return results;
    }

    public List<ItemStack> getItemStacksPlayerInventory(boolean includeCursorSlot) {
        return inventory.getInventoryStacks(includeCursorSlot);
    }

    public List<Slot> getSlotsThatCanFitInPlayerInventory(ItemStack stack, boolean acceptPartial) {
        return inventory.getSlotsThatCanFit(true, false, stack, acceptPartial);
    }

    public Optional<Slot> getSlotThatCanFitInPlayerInventory(ItemStack stack, boolean acceptPartial) {
        return getSlotsThatCanFitInPlayerInventory(stack, acceptPartial).stream().findFirst();
    }

    public List<Slot> getSlotsThatCanFitInOpenContainer(ItemStack stack, boolean acceptPartial) {
        return inventory.getSlotsThatCanFit(false, true, stack, acceptPartial);
    }

    public Optional<Slot> getSlotThatCanFitInOpenContainer(ItemStack stack, boolean acceptPartial) {
        return getSlotsThatCanFitInOpenContainer(stack, acceptPartial).stream().findFirst();
    }

    public List<Slot> getSlotsThatCanFitScreen(ItemStack stack, boolean acceptPartial) {
        return inventory.getSlotsThatCanFit(true, true, stack, acceptPartial);
    }

    public boolean hasEmptyInventorySlot() {
        return inventory.hasEmptySlot(true);
    }

    public void registerSlotAction() {
        inventory.setDirty();
    }

    public boolean hasItemContainer(Predicate<ContainerCache> accept, Item... items) {
        return containers.hasItem(accept, items);
    }

    public boolean hasItemContainer(Item... items) {
        return containers.hasItem(items);
    }

    public Optional<ContainerCache> getContainerAtPosition(BlockPos pos) {
        return containers.getContainerAtPosition(pos);
    }

    public boolean isContainerCached(BlockPos pos) {
        return getContainerAtPosition(pos).isPresent();
    }

    public Optional<ContainerCache> getEnderChestStorage() {
        return containers.getEnderChestStorage();
    }

    public List<ContainerCache> getCachedContainers(Predicate<ContainerCache> accept) {
        return containers.getCachedContainers(accept);
    }

    public List<ContainerCache> getCachedContainers(ContainerType... types) {
        return containers.getCachedContainers(types);
    }

    public List<ContainerCache> getCachedContainers() {
        return getCachedContainers(cache -> true);
    }

    public Optional<ContainerCache> getContainerClosestTo(Vec3 pos, Predicate<ContainerCache> accept) {
        return containers.getClosestTo(pos, accept);
    }

    public Optional<ContainerCache> getContainerClosestTo(Vec3 pos, ContainerType... types) {
        return containers.getClosestTo(pos, types);
    }

    public Optional<ContainerCache> getContainerClosestTo(Vec3 pos) {
        return getContainerClosestTo(pos, cache -> true);
    }

    public List<ContainerCache> getContainersWithItem(Item... items) {
        return containers.getContainersWithItem(items);
    }

    public Optional<ContainerCache> getClosestContainerWithItem(Vec3 pos, Item... items) {
        return containers.getClosestWithItem(pos, items);
    }

    public Optional<BlockPos> getLastBlockPosInteraction() {
        return Optional.ofNullable(containers.getLastBlockPosInteraction());
    }

    @Override
    protected void updateState() {
        inventory.setDirty();
        containers.setDirty();
    }

    @Override
    protected void reset() {
        inventory.reset();
        containers.reset();
    }
}
