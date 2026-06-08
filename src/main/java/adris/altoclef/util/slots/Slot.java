package adris.altoclef.util.slots;

import adris.altoclef.Debug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.Iterator;
import java.util.Objects;

public abstract class Slot {

    public static final int CURSOR_SLOT_INDEX = -1;
    private static final int UNDEFINED_SLOT_INDEX = -999;
    @SuppressWarnings("StaticInitializerReferencesSubClass")
    public static final Slot UNDEFINED = new PlayerSlot(UNDEFINED_SLOT_INDEX);

    private final int inventorySlot;
    private final int windowSlot;
    private final boolean isInventory;

    public Slot(int slot, boolean inventory) {
        isInventory = inventory;
        if (inventory) {
            inventorySlot = slot;
            windowSlot = UNDEFINED_SLOT_INDEX;
        } else {
            inventorySlot = UNDEFINED_SLOT_INDEX;
            windowSlot = slot;
        }
    }

    private static Slot getFromCurrentScreenAbstract(int slot, boolean inventory) {
        return switch (getCurrentType()) {
            case PLAYER -> new PlayerSlot(slot, inventory);
            case CRAFTING_TABLE -> new CraftingTableSlot(slot, inventory);
            case FURNACE_OR_SMITH_OR_SMOKER_OR_BLAST -> new FurnaceSlot(slot, inventory);
            case BREWING_STAND -> new BrewingStandSlot(slot, inventory);
            case CHEST_LARGE -> new ChestSlot(slot, true, inventory);
            case CHEST_SMALL -> new ChestSlot(slot, false, inventory);
        };
    }

    public static Slot getFromCurrentScreen(int windowSlot) {
        return getFromCurrentScreenAbstract(windowSlot, false);
    }

    public static Slot getFromCurrentScreenInventory(int inventorySlot) {
        return getFromCurrentScreenAbstract(inventorySlot, true);
    }

    private static ContainerType getCurrentType() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof AbstractFurnaceScreen || screen instanceof SmithingScreen) {
            return ContainerType.FURNACE_OR_SMITH_OR_SMOKER_OR_BLAST;
        }
        if (screen instanceof BrewingStandScreen) {
            return ContainerType.BREWING_STAND;
        }
        if (screen instanceof ContainerScreen containerScreen && containerScreen.getMenu() instanceof ChestMenu chestMenu) {
            return chestMenu.getRowCount() == 6 ? ContainerType.CHEST_LARGE : ContainerType.CHEST_SMALL;
        }
        if (screen instanceof CraftingScreen) {
            return ContainerType.CRAFTING_TABLE;
        }
        return ContainerType.PLAYER;
    }

    public static boolean isCursor(Slot slot) {
        return slot instanceof CursorSlot;
    }

    public static Iterable<Slot> getCurrentScreenSlots() {
        return () -> new Iterator<>() {
            final LocalPlayer player = Minecraft.getInstance().player;
            final AbstractContainerMenu handler = player != null ? player.containerMenu : null;
            final int max = handler != null ? handler.slots.size() : 0;
            int i = -1;

            @Override
            public boolean hasNext() {
                return i < max;
            }

            @Override
            public Slot next() {
                if (i == -1) {
                    ++i;
                    return CursorSlot.SLOT;
                }
                return Slot.getFromCurrentScreen(i++);
            }
        };
    }

    public int getInventorySlot() {
        if (!isInventory) {
            return windowSlotToInventorySlot(windowSlot);
        }
        return inventorySlot;
    }

    public int getWindowSlot() {
        if (isInventory) {
            return inventorySlotToWindowSlot(inventorySlot);
        }
        return windowSlot;
    }

    protected abstract int inventorySlotToWindowSlot(int inventorySlot);

    protected abstract int windowSlotToInventorySlot(int windowSlot);

    protected abstract String getName();

    @Override
    public String toString() {
        return getName() + (isInventory ? "InventorySlot" : "Slot") + "{inventory slot = "
                + getInventorySlot() + ", window slot = " + getWindowSlot() + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Slot slot)) return false;
        return getInventorySlot() == slot.getInventorySlot() && getWindowSlot() == slot.getWindowSlot();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getInventorySlot(), getWindowSlot());
    }

    public boolean isSlotInPlayerInventory() {
        LocalPlayer player = Minecraft.getInstance().player;
        AbstractContainerMenu handler = player != null ? player.containerMenu : null;
        if (handler instanceof InventoryMenu) {
            return true;
        }
        int slotCount = handler != null ? handler.slots.size() : 0;
        int window = getWindowSlot();
        boolean result = window >= (slotCount - 36);
        if (!result && window >= 0) {
            Debug.logWarning("Slot outside player inventory: " + this);
        }
        return result;
    }

    enum ContainerType {
        PLAYER,
        CRAFTING_TABLE,
        CHEST_SMALL,
        CHEST_LARGE,
        FURNACE_OR_SMITH_OR_SMOKER_OR_BLAST,
        BREWING_STAND
    }
}
