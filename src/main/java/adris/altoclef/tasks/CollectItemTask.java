package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.container.CraftRecipeBookTask;
import adris.altoclef.tasks.container.OpenCraftingTableTask;
import adris.altoclef.tasks.container.SmeltInFurnaceTask;
import adris.altoclef.tasks.entity.KillAndLootTask;
import adris.altoclef.tasks.resources.MineAndCollectTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

public class CollectItemTask extends Task {

    private final ItemTarget target;
    private int commandCooldown;
    private String lastCommand = "";
    private int noRouteCounter = 0;
    private static final int NO_ROUTE_WARNING_THRESHOLD = 10;

    public CollectItemTask(ItemTarget target) {
        this.target = target;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.itemTargetsMetInventory(mod, target);
    }

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
        lastCommand = "";
        noRouteCounter = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (isFinished(mod)) {
            noRouteCounter = 0;
            return null;
        }
        if (commandCooldown > 0) {
            commandCooldown--;
            return null;
        }
        Optional<ItemEntity> drop = mod.getEntityTracker().getClosestItemDrop(target);
        if (drop.isPresent()) {
            ItemEntity entity = drop.get();
            noRouteCounter = 0;
            runBaritone(mod, "goto " + entity.blockPosition().getX() + " " + entity.blockPosition().getY() + " " + entity.blockPosition().getZ(), "pickup drop");
            return null;
        }
        RecipeHolder<?> recipe = findRecipe(mod);
        if (recipe != null) {
            ItemTarget missing = firstMissingIngredient(mod, recipe);
            if (missing != null && !missing.isEmpty()) {
                noRouteCounter = 0;
                setDebugState("Collect ingredient " + missing + " for " + target);
                return new CollectItemTask(missing);
            }
            if (!recipe.value().canCraftInDimensions(2, 2) && !StorageHelper.isBigCraftingOpen()) {
                noRouteCounter = 0;
                setDebugState("Open crafting table for " + target);
                return new OpenCraftingTableTask();
            }
            noRouteCounter = 0;
            setDebugState("Craft " + target);
            return new CraftRecipeBookTask(target, false, recipe);
        }
        RecipeHolder<?> cookingRecipe = findCookingRecipe(mod);
        if (cookingRecipe != null) {
            noRouteCounter = 0;
            setDebugState("Smelt " + target);
            return new SmeltInFurnaceTask(target);
        }
        Task mobTask = mobDropTask();
        if (mobTask != null) {
            noRouteCounter = 0;
            return mobTask;
        }
        List<Block> blocks = mineableBlocks();
        if (!blocks.isEmpty()) {
            noRouteCounter = 0;
            setDebugState("Mining " + target);
            return new MineAndCollectTask(target, blocks.toArray(Block[]::new), MiningRequirement.HAND);
        }
        noRouteCounter++;
        if (noRouteCounter >= NO_ROUTE_WARNING_THRESHOLD) {
            if (mod.getPlayer() != null) {
                mod.getPlayer().displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§c[ALTOCLEF] CANNOT OBTAIN: " + target + " (no recipe, no drop, no mineable source)"),
                    false
                );
            }
            setDebugState("BLOCKED: " + target);
            commandCooldown = 200;
        } else {
            setDebugState("No direct route for " + target);
            commandCooldown = 80;
        }
        return null;
    }

    private RecipeHolder<?> findRecipe(AltoClef mod) {
        if (mod.getPlayer() == null || net.minecraft.client.Minecraft.getInstance().getConnection() == null) return null;
        RecipeHolder<?> best = null;
        int bestScore = Integer.MAX_VALUE;
        for (RecipeHolder<?> holder : net.minecraft.client.Minecraft.getInstance().getConnection().getRecipeManager().getRecipes()) {
            if (!holder.id().getNamespace().equals("minecraft")) continue; // vanilla-default: ignore modded/pack recipes
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof CraftingRecipe)) continue;
            ItemStack result = recipe.getResultItem(mod.getPlayer().registryAccess());
            if (result.isEmpty() || !target.matches(result.getItem())) continue;
            if (recipeReferencesTarget(recipe)) continue;
            int score = scoreRecipe(recipe);
            if (score < bestScore) {
                bestScore = score;
                best = holder;
            }
        }
        return best;
    }

    private RecipeHolder<?> findCookingRecipe(AltoClef mod) {
        if (mod.getPlayer() == null || net.minecraft.client.Minecraft.getInstance().getConnection() == null) return null;
        RecipeHolder<?> best = null;
        int bestScore = Integer.MAX_VALUE;
        for (RecipeHolder<?> holder : net.minecraft.client.Minecraft.getInstance().getConnection().getRecipeManager().getRecipes()) {
            if (!holder.id().getNamespace().equals("minecraft")) continue; // vanilla-default: ignore modded/pack recipes
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof AbstractCookingRecipe)) continue;
            ItemStack result = recipe.getResultItem(mod.getPlayer().registryAccess());
            if (result.isEmpty() || !target.matches(result.getItem())) continue;
            if (recipeReferencesTarget(recipe)) continue;
            int score = scoreRecipe(recipe);
            if (score < bestScore) {
                bestScore = score;
                best = holder;
            }
        }
        return best;
    }

    private boolean recipeReferencesTarget(Recipe<?> recipe) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            if (Arrays.stream(ingredient.getItems())
                    .filter(stack -> !stack.isEmpty())
                    .map(ItemStack::getItem)
                    .anyMatch(target::matches)) {
                return true;
            }
        }
        return false;
    }

    private int scoreRecipe(Recipe<?> recipe) {
        int score = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] stacks = ingredient.getItems();
            Item[] items = Arrays.stream(stacks)
                    .filter(stack -> !stack.isEmpty())
                    .map(ItemStack::getItem)
                    .distinct()
                    .toArray(Item[]::new);
            if (items.length == 0) continue;
            boolean hasVanilla = false;
            boolean hasModded = false;
            for (Item item : items) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id != null && id.getNamespace().equals("minecraft")) {
                    hasVanilla = true;
                } else {
                    hasModded = true;
                }
            }
            if (!hasVanilla) {
                return Integer.MAX_VALUE;
            }
            if (hasModded) {
                score += 1;
            }
        }
        return score;
    }

    private ItemTarget firstMissingIngredient(AltoClef mod, RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        int need = target.getTargetCount() - Arrays.stream(target.getMatches())
                .mapToInt(mod.getItemStorage()::getItemCountInventoryOnly)
                .sum();
        int repeats = Math.max(1, (int) Math.floor(-0.1 + (double) need / Math.max(1, getRecipeOutputCount(mod, recipe))) + 1);

        Map<Item, Integer> reserved = new HashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] stacks = ingredient.getItems();
            Item[] items = Arrays.stream(stacks)
                    .filter(stack -> !stack.isEmpty())
                    .map(ItemStack::getItem)
                    .distinct()
                    .toArray(Item[]::new);
            if (items.length == 0) continue;
            Item chosen = chooseIngredientItem(mod, items);
            if (chosen == null) return null;
            int nextReserved = reserved.getOrDefault(chosen, 0) + repeats;
            int available = mod.getItemStorage().getItemCountInventoryOnly(chosen);
            if (available < nextReserved) {
                return new ItemTarget(chosen, nextReserved - available);
            }
            reserved.put(chosen, nextReserved);
        }
        return null;
    }

    private Item chooseIngredientItem(AltoClef mod, Item[] items) {
        Item firstVanilla = null;
        Item firstModded = null;
        for (Item item : items) {
            if (item == null) continue;
            int available = mod.getItemStorage().getItemCountInventoryOnly(item);
            if (available > 0) return item;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null && id.getNamespace().equals("minecraft")) {
                if (firstVanilla == null) firstVanilla = item;
            } else if (firstModded == null) {
                firstModded = item;
            }
        }
        return firstVanilla != null ? firstVanilla : firstModded;
    }

    private int getRecipeOutputCount(AltoClef mod, Recipe<?> recipe) {
        ItemStack result = recipe.getResultItem(mod.getPlayer().registryAccess());
        return result.isEmpty() ? 1 : result.getCount();
    }

    private boolean hasOnlyModdedIngredientsInAny(Recipe<?> recipe) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] stacks = ingredient.getItems();
            Item[] items = Arrays.stream(stacks)
                    .filter(stack -> !stack.isEmpty())
                    .map(ItemStack::getItem)
                    .distinct()
                    .toArray(Item[]::new);
            if (items.length == 0) continue;
            // Check if this ingredient slot has ANY vanilla items
            boolean hasVanilla = false;
            for (Item item : items) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id != null && id.getNamespace().equals("minecraft")) {
                    hasVanilla = true;
                    break;
                }
            }
            // If this slot has only modded items, skip the recipe
            if (!hasVanilla) {
                return true;
            }
        }
        return false;
    }

    private List<Block> mineableBlocks() {
        List<Block> result = new ArrayList<>();
        for (Item item : target.getMatches()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                Block block = BuiltInRegistries.BLOCK.get(id);
                if (!block.defaultBlockState().isAir() && !isContainerLikeBlock(block) && !result.contains(block)) {
                    result.add(block);
                }
            }
            addBlockFallbacks(result, id);
        }
        return result;
    }

    private static boolean isContainerLikeBlock(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("chest")
                || path.contains("barrel")
                || path.contains("shulker_box")
                || path.contains("crate")
                || path.contains("drawer");
    }

    private static void addBlockFallbacks(List<Block> result, ResourceLocation itemId) {
        if (itemId == null) return;
        String ns = itemId.getNamespace();
        String path = itemId.getPath();
        if (ns.equals("minecraft")) {
            switch (path) {
                case "coal" -> addBlocks(result, "minecraft:coal_ore", "minecraft:deepslate_coal_ore");
                case "cobblestone" -> addBlocks(result, "minecraft:stone");
                case "flint" -> addBlocks(result, "minecraft:gravel");
                case "clay_ball" -> addBlocks(result, "minecraft:clay");
                case "raw_copper", "copper_ingot" -> addBlocks(result, "minecraft:copper_ore", "minecraft:deepslate_copper_ore");
                case "raw_iron", "iron_ingot" -> addBlocks(result, "minecraft:iron_ore", "minecraft:deepslate_iron_ore");
                case "raw_gold", "gold_ingot" -> addBlocks(result, "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore");
                case "diamond" -> addBlocks(result, "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore");
                case "emerald" -> addBlocks(result, "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore");
                case "redstone" -> addBlocks(result, "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore");
                case "lapis_lazuli" -> addBlocks(result, "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore");
                case "quartz" -> addBlocks(result, "minecraft:nether_quartz_ore");
                case "netherite_scrap" -> addBlocks(result, "minecraft:ancient_debris");
                case "apple" -> addBlocks(result, "minecraft:oak_leaves", "minecraft:dark_oak_leaves");
                case "carrot" -> addBlocks(result, "minecraft:carrots");
                case "potato", "baked_potato" -> addBlocks(result, "minecraft:potatoes");
                case "beetroot" -> addBlocks(result, "minecraft:beetroots");
                case "wheat", "bread" -> addBlocks(result, "minecraft:wheat");
                case "wheat_seeds" -> addBlocks(result, "minecraft:short_grass", "minecraft:tall_grass", "minecraft:wheat");
                case "cocoa_beans" -> addBlocks(result, "minecraft:cocoa");
                case "melon_slice" -> addBlocks(result, "minecraft:melon");
                case "pumpkin", "pumpkin_pie" -> addBlocks(result, "minecraft:pumpkin");
                default -> {
                }
            }
        } else if (ns.equals("allthemodium")) {
            switch (path) {
                case "allthemodium_ingot", "raw_allthemodium" -> addBlocks(result, "allthemodium:allthemodium_ore", "allthemodium:allthemodium_slate_ore");
                case "vibranium_ingot", "raw_vibranium" -> addBlocks(result, "allthemodium:vibranium_ore");
                case "unobtainium_ingot", "raw_unobtainium" -> addBlocks(result, "allthemodium:unobtainium_ore");
                default -> {
                }
            }
        }
    }

    private static void addBlocks(List<Block> result, String... ids) {
        for (String idText : ids) {
            ResourceLocation id = ResourceLocation.tryParse(idText);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                Block block = BuiltInRegistries.BLOCK.get(id);
                if (!result.contains(block)) result.add(block);
            }
        }
    }

    private Task mobDropTask() {
        for (Item item : target.getMatches()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || !id.getNamespace().equals("minecraft")) continue;
            String mob = switch (id.getPath()) {
                case "blaze_rod" -> "minecraft:blaze";
                case "ender_pearl" -> "minecraft:enderman";
                case "bone" -> "minecraft:skeleton";
                case "gunpowder" -> "minecraft:creeper";
                case "spider_eye", "string" -> "minecraft:spider";
                case "rotten_flesh" -> "minecraft:zombie";
                case "slime_ball" -> "minecraft:slime";
                case "leather", "beef" -> "minecraft:cow";
                case "porkchop" -> "minecraft:pig";
                case "chicken", "feather" -> "minecraft:chicken";
                case "mutton", "white_wool" -> "minecraft:sheep";
                default -> null;
            };
            if (mob != null) return new KillAndLootTask(mob, new ItemTarget(item, target.getTargetCount()));
        }
        return null;
    }

    private void runBaritone(AltoClef mod, String command, String debug) {
        if (!command.equals(lastCommand)) {
            mod.runBaritone(command);
            lastCommand = command;
        }
        setDebugState(debug);
        commandCooldown = 80;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CollectItemTask task
                && Arrays.equals(task.target.getMatches(), target.getMatches())
                && task.target.getTargetCount() == target.getTargetCount();
    }

    @Override
    protected String toDebugString() {
        Item item = target.getMatches().length == 0 ? Items.AIR : target.getMatches()[0];
        return "Collect " + target + " first=" + ItemHelper.toResourceName(item);
    }
}
