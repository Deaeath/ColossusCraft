package adris.altoclef.util.helpers;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.util.RecipeTarget;
import adris.altoclef.util.slots.CursorSlot;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.mixins.AbstractFurnaceScreenHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

@SuppressWarnings("ConstantConditions")
public final class StorageHelper {
    public static final List<PlayerSlot> INACCESSIBLE_PLAYER_SLOTS =
            Stream.concat(Stream.of(PlayerSlot.CRAFT_INPUT_SLOTS), Stream.of(PlayerSlot.ARMOR_SLOTS)).toList();

    private StorageHelper() {
    }

    public static void closeScreen() {
        LocalPlayer player = Minecraft.getInstance().player;
        Screen screen = Minecraft.getInstance().screen;
        if (player != null && screen != null && !(screen instanceof PauseScreen) && !(screen instanceof OptionsScreen) && !(screen instanceof ChatScreen)) {
            player.closeContainer();
        }
    }

    public static ItemStack getItemStackInCursorSlot() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.containerMenu == null) {
            return ItemStack.EMPTY;
        }
        return player.containerMenu.getCarried();
    }

    public static ItemStack getItemStackInSlot(Slot slot) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return ItemStack.EMPTY;
        if (Slot.isCursor(slot)) return getItemStackInCursorSlot();
        Inventory inv = player.getInventory();
        if (slot.equals(PlayerSlot.OFFHAND_SLOT)) return inv.offhand.stream().findFirst().orElse(ItemStack.EMPTY);
        if (slot.equals(PlayerSlot.ARMOR_HELMET_SLOT)) return inv.getArmor(3);
        if (slot.equals(PlayerSlot.ARMOR_CHESTPLATE_SLOT)) return inv.getArmor(2);
        if (slot.equals(PlayerSlot.ARMOR_LEGGINGS_SLOT)) return inv.getArmor(1);
        if (slot.equals(PlayerSlot.ARMOR_BOOTS_SLOT)) return inv.getArmor(0);
        try {
            AbstractContainerMenu menu = player.containerMenu;
            int window = slot.getWindowSlot();
            if (menu != null && window >= 0 && window < menu.slots.size()) {
                return menu.getSlot(window).getItem();
            }
        } catch (Exception e) {
            Debug.logWarning("Screen slot error: " + e.getMessage());
        }
        return ItemStack.EMPTY;
    }

    public static boolean isPlayerInventoryOpen() {
        return Minecraft.getInstance().screen instanceof InventoryScreen || Minecraft.getInstance().screen == null;
    }

    public static boolean isBigCraftingOpen() {
        return Minecraft.getInstance().screen instanceof CraftingScreen;
    }

    public static boolean isFurnaceOpen() {
        return Minecraft.getInstance().screen instanceof AbstractFurnaceScreen;
    }

    public static boolean isSmokerOpen() {
        return isFurnaceOpen();
    }

    public static boolean isBlastFurnaceOpen() {
        return isFurnaceOpen();
    }

    public static boolean isBrewingStandOpen() {
        return Minecraft.getInstance().screen instanceof BrewingStandScreen;
    }

    public static boolean isSmithingTableOpen() {
        return Minecraft.getInstance().screen instanceof SmithingScreen;
    }

    public static MiningRequirement getCurrentMiningRequirement(AltoClef mod) {
        MiningRequirement[] order = {MiningRequirement.NETHERITE, MiningRequirement.DIAMOND, MiningRequirement.IRON, MiningRequirement.STONE, MiningRequirement.WOOD};
        for (MiningRequirement requirement : order) {
            if (miningRequirementMet(mod, requirement)) return requirement;
        }
        return MiningRequirement.HAND;
    }

    private static boolean has(AltoClef mod, boolean inventoryOnly, Item... items) {
        return inventoryOnly ? mod.getItemStorage().hasItemInventoryOnly(items) : mod.getItemStorage().hasItem(items);
    }

    private static boolean miningRequirementMetInner(AltoClef mod, boolean inventoryOnly, MiningRequirement requirement) {
        return switch (requirement) {
            case HAND -> true;
            case WOOD -> has(mod, inventoryOnly, Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);
            case STONE -> has(mod, inventoryOnly, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);
            case IRON -> has(mod, inventoryOnly, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);
            case DIAMOND -> has(mod, inventoryOnly, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);
            case NETHERITE -> has(mod, inventoryOnly, Items.NETHERITE_PICKAXE);
        };
    }

    public static boolean miningRequirementMet(AltoClef mod, MiningRequirement requirement) {
        return miningRequirementMetInner(mod, false, requirement);
    }

    public static boolean miningRequirementMetInventory(AltoClef mod, MiningRequirement requirement) {
        return miningRequirementMetInner(mod, true, requirement);
    }

    public static boolean itemTargetsMet(AltoClef mod, ItemTarget... targets) {
        return Arrays.stream(targets).allMatch(target -> mod.getItemStorage().getItemCount(target.getMatches()) >= target.getTargetCount());
    }

    public static boolean itemTargetsMetInventory(AltoClef mod, ItemTarget... targets) {
        return Arrays.stream(targets).allMatch(target -> mod.getItemStorage().getItemCountInventoryOnly(target.getMatches()) >= target.getTargetCount());
    }

    public static boolean itemTargetsMetInventoryNoCursor(AltoClef mod, ItemTarget... targets) {
        return itemTargetsMetInventory(mod, targets) && Arrays.stream(targets).noneMatch(target -> target.matches(getItemStackInCursorSlot().getItem()));
    }

    public static boolean hasCataloguedItem(AltoClef mod, String catalogueName) {
        return mod.getItemStorage().hasItem(TaskCatalogue.getItemMatches(catalogueName));
    }

    public static boolean isEquipped(AltoClef mod, Item... items) {
        Item equipped = getItemStackInSlot(PlayerSlot.getEquipSlot()).getItem();
        return Arrays.stream(items).anyMatch(item -> item == equipped);
    }

    public static boolean isArmorEquipped(AltoClef mod, Item item) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (item instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getEquipmentSlot();
            ItemStack stack = player.getItemBySlot(slot);
            return stack.getItem() == item;
        }
        return Arrays.stream(PlayerSlot.ARMOR_SLOTS).anyMatch(slot -> getItemStackInSlot(slot).getItem() == item);
    }

    public static boolean isArmorEquipped(AltoClef mod, ItemTarget target) {
        return Arrays.stream(target.getMatches()).anyMatch(item -> isArmorEquipped(mod, item));
    }

    public static boolean isArmorEquipped(AltoClef mod, Item[] items) {
        return Arrays.stream(items).anyMatch(item -> isArmorEquipped(mod, item));
    }

    public static boolean isArmorEquippedAll(AltoClef mod, ItemTarget... targets) {
        return Arrays.stream(targets).allMatch(target -> isArmorEquipped(mod, target));
    }

    public static boolean isArmorEquippedAll(AltoClef mod, Item[] items) {
        return Arrays.stream(items).allMatch(item -> isArmorEquipped(mod, item));
    }

    public static Optional<Slot> getGarbageSlot(AltoClef mod) {
        for (Slot slot : Slot.getCurrentScreenSlots()) {
            ItemStack stack = getItemStackInSlot(slot);
            if (!Slot.isCursor(slot) && slot.isSlotInPlayerInventory() && ItemHelper.canThrowAwayStack(mod, stack)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    public static Optional<Slot> getBestToolSlot(AltoClef mod, BlockState state) {
        Optional<Slot> nonNetherite = getBestToolSlot(mod, state, !state.is(Tags.Blocks.NEEDS_NETHERITE_TOOL));
        return nonNetherite.isPresent() ? nonNetherite : getBestToolSlot(mod, state, false);
    }

    private static Optional<Slot> getBestToolSlot(AltoClef mod, BlockState state, boolean skipNetherite) {
        Slot best = null;
        float bestSpeed = 1.0f;
        for (Slot slot : Slot.getCurrentScreenSlots()) {
            if (!slot.isSlotInPlayerInventory()) continue;
            ItemStack stack = getItemStackInSlot(slot);
            if (stack.getItem() instanceof DiggerItem) {
                if (skipNetherite && isVanillaNetheriteTool(stack)) continue;
                float speed = stack.getDestroySpeed(state);
                if (stack.isCorrectToolForDrops(state) && speed > bestSpeed) {
                    bestSpeed = speed;
                    best = slot;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean isVanillaNetheriteTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.NETHERITE_PICKAXE
            || item == Items.NETHERITE_AXE
            || item == Items.NETHERITE_SHOVEL
            || item == Items.NETHERITE_HOE;
    }

    public static Optional<Slot> getFilledInventorySlotInaccessibleToContainer(AltoClef mod, ItemTarget target) {
        for (Slot slot : INACCESSIBLE_PLAYER_SLOTS) {
            ItemStack stack = getItemStackInSlot(slot);
            if (!stack.isEmpty() && target.matches(stack.getItem())) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    public static boolean isItemInaccessibleToContainer(AltoClef mod, ItemTarget target) {
        return getFilledInventorySlotInaccessibleToContainer(mod, target).isPresent();
    }

    public static List<ItemTarget> getAllInventoryItemsAsTargets(Predicate<Slot> accept) {
        List<ItemTarget> result = new ArrayList<>();
        for (Slot slot : Slot.getCurrentScreenSlots()) {
            if (!slot.isSlotInPlayerInventory() || !accept.test(slot)) continue;
            ItemStack stack = getItemStackInSlot(slot);
            if (!stack.isEmpty()) {
                result.add(new ItemTarget(stack.getItem(), stack.getCount()));
            }
        }
        return result;
    }

    public static int getBuildingMaterialCount(AltoClef mod) {
        return mod.getItemStorage().getItemStacksPlayerInventory(false).stream()
                .filter(stack -> stack.getItem() instanceof BlockItem)
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public static int calculateInventoryFoodScore(AltoClef mod) {
        return mod.getItemStorage().getItemStacksPlayerInventory(false).stream()
                .filter(stack -> stack.getComponents().has(net.minecraft.core.component.DataComponents.FOOD))
                .mapToInt(stack -> stack.getCount() * stack.get(net.minecraft.core.component.DataComponents.FOOD).nutrition())
                .sum();
    }

    public static int calculateInventoryFuelCount(AltoClef mod) {
        return mod.getItemStorage().getItemCount(Items.COAL, Items.CHARCOAL, Items.COAL_BLOCK, Items.LAVA_BUCKET);
    }

    public static boolean hasRecipeMaterialsOrTarget(AltoClef mod, CraftingRecipe recipe) {
        return false;
    }

    public static boolean hasRecipeMaterialsOrTarget(AltoClef mod, ItemTarget... targets) {
        return itemTargetsMet(mod, targets);
    }

    public static boolean hasRecipeMaterialsOrTarget(AltoClef mod, RecipeTarget... targets) {
        if (targets == null) return true;
        for (RecipeTarget target : targets) {
            if (target == null) continue;
            if (mod.getItemStorage().getItemCount(target.getOutputItem()) >= target.getTargetCount()) continue;
            CraftingRecipe recipe = target.getRecipe();
            if (recipe == null) return false;
            int repeats = (int) Math.ceil((target.getTargetCount() - mod.getItemStorage().getItemCount(target.getOutputItem())) / (double) recipe.outputCount());
            for (int i = 0; i < recipe.getSlotCount(); i++) {
                ItemTarget slot = recipe.getSlot(i);
                if (slot == null || slot.isEmpty()) continue;
                if (mod.getItemStorage().getItemCount(slot.getMatches()) < repeats) return false;
            }
        }
        return true;
    }

    public static void instantFillRecipeViaBook(AltoClef mod, CraftingRecipe recipe, Item outputItem, boolean craftAll) {
    }

    // Furnace/smoker/blast all extend AbstractFurnaceMenu; the open menu (if any) is whichever one.
    private static ContainerData furnaceData() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof AbstractFurnaceMenu furnace) {
            return ((AbstractFurnaceScreenHandlerAccessor) furnace).getData();
        }
        return null;
    }

    // ContainerData: [0]=litTime [1]=litDuration [2]=cookingProgress [3]=cookingTotalTime
    private static double cookPercent() {
        ContainerData d = furnaceData();
        if (d == null) return -1;
        int total = d.get(3);
        return total <= 0 ? 0 : (double) d.get(2) / total;
    }

    private static double fuelPercent() {
        ContainerData d = furnaceData();
        if (d == null) return -1;
        int duration = d.get(1);
        return duration <= 0 ? (double) d.get(0) / 200.0 : (double) d.get(0) / duration;
    }

    public static double getFurnaceCookPercent() {
        return cookPercent();
    }

    public static double getFurnaceFuel() {
        return fuelPercent();
    }

    public static double getSmokerCookPercent() {
        return cookPercent();
    }

    public static double getSmokerFuel() {
        return fuelPercent();
    }

    public static double getBlastFurnaceCookPercent() {
        return cookPercent();
    }

    public static double getBlastFurnaceFuel() {
        return fuelPercent();
    }
}
