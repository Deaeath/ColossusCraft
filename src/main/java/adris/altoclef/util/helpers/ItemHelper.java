package adris.altoclef.util.helpers;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import adris.altoclef.AltoClef;
import adris.altoclef.util.WoodType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public final class ItemHelper {
    private ItemHelper() {
    }

    /** Resolve an item by its (default-namespace) registry path, or null if absent. */
    private static Item item(String path) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.withDefaultNamespace(path)).orElse(null);
    }

    private static final java.util.HashMap<Item, Item> _cookableFoodMap = new java.util.HashMap<>() {
        {
            put(Items.PORKCHOP, Items.COOKED_PORKCHOP);
            put(Items.BEEF, Items.COOKED_BEEF);
            put(Items.CHICKEN, Items.COOKED_CHICKEN);
            put(Items.MUTTON, Items.COOKED_MUTTON);
            put(Items.RABBIT, Items.COOKED_RABBIT);
            put(Items.SALMON, Items.COOKED_SALMON);
            put(Items.COD, Items.COOKED_COD);
            put(Items.POTATO, Items.BAKED_POTATO);
        }
    };
    public static final Item[] RAW_FOODS = _cookableFoodMap.keySet().toArray(Item[]::new);

    public static java.util.Optional<Item> getCookedFood(Item rawFood) {
        return java.util.Optional.ofNullable(_cookableFoodMap.getOrDefault(rawFood, null));
    }

    public static boolean isCookableFood(Item item) {
        return _cookableFoodMap.containsKey(item);
    }

    private static java.util.Map<Item, Integer> _fuelTimeMap = null;

    private static java.util.Map<Item, Integer> getFuelTimeMap() {
        if (_fuelTimeMap == null) {
            _fuelTimeMap = net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.getFuel();
        }
        return _fuelTimeMap;
    }

    public static double getFuelAmount(Item... items) {
        double total = 0;
        for (Item item : items) {
            if (getFuelTimeMap().containsKey(item)) {
                int timeTicks = getFuelTimeMap().get(item);
                // 200 ticks -> 1 operation
                total += (double) timeTicks / 200.0;
            }
        }
        return total;
    }

    public static double getFuelAmount(ItemStack stack) {
        return getFuelAmount(stack.getItem()) * stack.getCount();
    }

    public static boolean isFuel(Item item) {
        return getFuelTimeMap().containsKey(item);
    }

    public static final Item[] SAPLINGS = new Item[]{Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING,
            Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING, Items.MANGROVE_PROPAGULE};
    public static final Block[] SAPLING_SOURCES = new Block[]{Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES,
            Blocks.BIRCH_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
            Blocks.MANGROVE_PROPAGULE};

    // Modern 1.21.1 Registry Fields required by TaskCatalogue
    public static Item[] LOG = BuiltInRegistries.ITEM.stream().filter(i -> i.getDescriptionId().contains("log")).toArray(Item[]::new);
    public static Item[] WOOL = BuiltInRegistries.ITEM.stream().filter(i -> i.getDescriptionId().contains("wool")).toArray(Item[]::new);
    public static Item[] PLANKS = BuiltInRegistries.ITEM.stream().filter(i -> i.getDescriptionId().contains("planks")).toArray(Item[]::new);
    public static Item[] LEAVES = BuiltInRegistries.ITEM.stream().filter(i -> i.getDescriptionId().contains("leaves")).toArray(Item[]::new);
    public static Item[] FLOWER = BuiltInRegistries.ITEM.stream().filter(i -> i.getDescriptionId().contains("flower")).toArray(Item[]::new);
    public static Item[] IRON_ARMORS = new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
    public static Item[] DIAMOND_ARMORS = new Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
    public static Item[] HOSTILE_MOB_DROPS = new Item[]{Items.BONE, Items.ARROW, Items.STRING, Items.SPIDER_EYE, Items.ROTTEN_FLESH, Items.GUNPOWDER, Items.SLIME_BALL, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.MAGMA_CREAM};

    public static Item[] WOOD_BOAT = new Item[]{Items.OAK_BOAT, Items.BIRCH_BOAT, Items.SPRUCE_BOAT, Items.JUNGLE_BOAT};
    public static Item[] WOOD_PRESSURE_PLATE = new Item[]{Items.OAK_PRESSURE_PLATE, Items.BIRCH_PRESSURE_PLATE, Items.SPRUCE_PRESSURE_PLATE};
    public static Item[] WOOD_BUTTON = new Item[]{Items.OAK_BUTTON, Items.BIRCH_BUTTON, Items.SPRUCE_BUTTON};
    public static Item[] WOOD_SIGN = new Item[]{Items.OAK_SIGN, Items.BIRCH_SIGN, Items.SPRUCE_SIGN};

    public static Item[] BED = colorArray(c -> c.bed);
    public static Item[] WOOD_STAIRS = woodArray(w -> w.stairs);
    public static Item[] WOOD_SLAB = woodArray(w -> w.slab);
    public static Item[] WOOD_DOOR = woodArray(w -> w.door);
    public static Item[] WOOD_TRAPDOOR = woodArray(w -> w.trapdoor);
    public static Item[] WOOD_FENCE = woodArray(w -> w.fence);
    public static Item[] WOOD_FENCE_GATE = woodArray(w -> w.fenceGate);

    // Structural nested tracking classes required for lambda expressions
    public static class ColorfulItems {
        public String colorName;
        public DyeColor color;
        public Item wool;
        public Item bed;

        ColorfulItems(DyeColor color) {
            this.color = color;
            this.colorName = color.getName();
            this.wool = item(colorName + "_wool");
            this.bed = item(colorName + "_bed");
        }
    }

    public static class WoodItems {
        public String prefix;
        public Item log;
        public Item planks;
        public Item leaves;
        public Item boat;
        public Item pressurePlate;
        public Item button;
        public Item sign;
        public Item stairs;
        public Item slab;
        public Item door;
        public Item trapdoor;
        public Item fence;
        public Item fenceGate;
        private final boolean nether;

        WoodItems(String prefix, boolean nether) {
            this.prefix = prefix;
            this.nether = nether;
            this.log = item(prefix + (nether ? "_stem" : "_log"));
            this.planks = item(prefix + "_planks");
            this.leaves = nether ? null : item(prefix + "_leaves");
            this.boat = item(prefix + "_boat");
            this.pressurePlate = item(prefix + "_pressure_plate");
            this.button = item(prefix + "_button");
            this.sign = item(prefix + "_sign");
            this.stairs = item(prefix + "_stairs");
            this.slab = item(prefix + "_slab");
            this.door = item(prefix + "_door");
            this.trapdoor = item(prefix + "_trapdoor");
            this.fence = item(prefix + "_fence");
            this.fenceGate = item(prefix + "_fence_gate");
        }

        public boolean isNetherWood() {
            return nether;
        }
    }

    public static ColorfulItems getColorfulItems(MapColor mapColor) {
        for (DyeColor dye : DyeColor.values()) {
            if (dye.getMapColor() == mapColor) {
                return new ColorfulItems(dye);
            }
        }
        return new ColorfulItems(DyeColor.WHITE);
    }

    public static ColorfulItems getColorfulItems(DyeColor color) {
        return new ColorfulItems(color);
    }

    public static ColorfulItems[] getColorfulItems() {
        return Arrays.stream(DyeColor.values()).map(ColorfulItems::new).toArray(ColorfulItems[]::new);
    }

    public static WoodItems getWoodItems(WoodType type) {
        boolean nether = type == WoodType.CRIMSON || type == WoodType.WARPED;
        return new WoodItems(type.name().toLowerCase(Locale.ROOT), nether);
    }

    public static WoodItems[] getWoodItems() {
        return Arrays.stream(WoodType.values()).map(ItemHelper::getWoodItems).toArray(WoodItems[]::new);
    }

    public static Item logToPlanks(Item log) {
        for (WoodItems wood : getWoodItems()) {
            if (wood.log == log) return wood.planks;
        }
        return null;
    }

    public static Item planksToLog(Item planks) {
        for (WoodItems wood : getWoodItems()) {
            if (wood.planks == planks) return wood.log;
        }
        return null;
    }

    public static boolean areShearsEffective(Block block) {
        BlockState state = block.defaultBlockState();
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.WOOL)
                || block == Blocks.COBWEB
                || block == Blocks.VINE
                || block == Blocks.TRIPWIRE;
    }

    private static Item[] woodArray(Function<WoodItems, Item> getter) {
        return Arrays.stream(WoodType.values()).map(ItemHelper::getWoodItems).map(getter).filter(Objects::nonNull).toArray(Item[]::new);
    }

    private static Item[] colorArray(Function<ColorfulItems, Item> getter) {
        return Arrays.stream(DyeColor.values()).map(ColorfulItems::new).map(getter).filter(Objects::nonNull).toArray(Item[]::new);
    }

    public static Block[] itemsToBlocks(Item[] items) {
        return Arrays.stream(items).map(Block::byItem).filter(b -> b != Blocks.AIR).toArray(Block[]::new);
    }

    public static Item[] blocksToItems(Block[] blocks) {
        return Arrays.stream(blocks).map(Block::asItem).filter(item -> item != Items.AIR).toArray(Item[]::new);
    }

    // Your original custom methods preserved intact
    public static String trimItemName(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    public static String stripItemName(Item item) {
        return trimItemName(item.getDescriptionId());
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
