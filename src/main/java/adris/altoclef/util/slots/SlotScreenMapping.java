package adris.altoclef.util.slots;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SlotScreenMapping {

    private static final List<SlotScreenMappingEntry> CLASS_LIST = List.of(
            e(CraftingTableSlot.class, screen -> screen instanceof CraftingScreen, CraftingTableSlot::new),
            e(FurnaceSlot.class, screen -> screen instanceof AbstractFurnaceScreen, FurnaceSlot::new),
            e(SmokerSlot.class, screen -> screen instanceof AbstractFurnaceScreen, SmokerSlot::new),
            e(BlastFurnaceSlot.class, screen -> screen instanceof AbstractFurnaceScreen, BlastFurnaceSlot::new),
            e(SmithingTableSlot.class, screen -> screen instanceof SmithingScreen, SmithingTableSlot::new),
            e(BrewingStandSlot.class, screen -> screen instanceof BrewingStandScreen, BrewingStandSlot::new),
            e(ChestSlot.class, screen -> screen instanceof ContainerScreen, (slot, inv) -> new ChestSlot(slot, false, inv)),
            e(PlayerSlot.class, screen -> true, PlayerSlot::new),
            e(CursorSlot.class, screen -> true, (slot, inv) -> CursorSlot.SLOT)
    );

    public static boolean isScreenOpen(Class slotType) {
        Screen screen = Minecraft.getInstance().screen;
        for (SlotScreenMappingEntry entry : CLASS_LIST) {
            if (slotType == entry.type || slotType.isAssignableFrom(entry.type)) {
                return entry.inScreen.test(screen);
            }
        }
        throw new UnsupportedOperationException("Slot type class not registered: " + slotType);
    }

    public static Slot getFromScreen(int slot, boolean inventory) {
        Screen screen = Minecraft.getInstance().screen;
        for (SlotScreenMappingEntry entry : CLASS_LIST) {
            if (entry.inScreen.test(screen)) {
                return entry.getSlot.apply(slot, inventory);
            }
        }
        throw new UnsupportedOperationException("No screen slot mapping for " + screen);
    }

    private static SlotScreenMappingEntry e(Class type, Predicate<Screen> inScreen, BiFunction<Integer, Boolean, Slot> getSlot) {
        return new SlotScreenMappingEntry(type, inScreen, getSlot);
    }

    static class SlotScreenMappingEntry {
        final Class type;
        final Predicate<Screen> inScreen;
        final BiFunction<Integer, Boolean, Slot> getSlot;

        SlotScreenMappingEntry(Class type, Predicate<Screen> inScreen, BiFunction<Integer, Boolean, Slot> getSlot) {
            this.type = type;
            this.inScreen = inScreen;
            this.getSlot = getSlot;
        }
    }
}
