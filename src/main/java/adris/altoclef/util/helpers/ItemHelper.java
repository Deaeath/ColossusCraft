package adris.altoclef.util.helpers;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import adris.altoclef.AltoClef;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ItemHelper {
    private ItemHelper() {
    }

    public static String trimItemName(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    public static String toResourceName(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? "" : key.toString();
    }

    public static String toResourceName(ItemStack stack) {
        return stack.isEmpty() ? "" : toResourceName(stack.getItem());
    }

    public static boolean canStackTogether(ItemStack left, ItemStack right) {
        return !left.isEmpty()
                && !right.isEmpty()
                && ItemStack.isSameItemSameComponents(left, right)
                && right.getCount() < right.getMaxStackSize();
    }

    public static boolean canThrowAwayStack(AltoClef mod, ItemStack stack) {
        return stack.isEmpty() || mod.getBehaviour().isProtected(stack.getItem()) == false;
    }
}
