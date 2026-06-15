package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.SmeltTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.slots.SmokerSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.block.Blocks;
import baritone.api.utils.input.Input;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * Smelt food in a smoker (places/opens one, inserts material + fuel, collects the cooked output).
 * Mirror of {@link SmeltInFurnaceTask} for the food-cooking smoker. Ported to NeoForge/MojMap 1.21.1.
 */
public class SmeltInSmokerTask extends ResourceTask {
    private static final Item[] FUELS = {Items.COAL, Items.CHARCOAL, Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS};
    private final ItemTarget target;
    private int useCooldown;

    public SmeltInSmokerTask(ItemTarget target) {
        super(target);
        this.target = target;
    }

    public SmeltInSmokerTask(SmeltTarget smeltTarget) {
        this(smeltTarget.getItem());
    }

    /** API parity with upstream; this task always reads the input from the recipe. */
    public void ignoreMaterials() {
    }

    @Override
    protected void onResourceStart(AltoClef mod) {
        useCooldown = 0;
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        if (isFinished(mod)) return null;
        RecipeHolder<?> holder = findCookingRecipe(mod);
        if (holder == null) return null;
        ItemTarget material = firstIngredient(holder);
        if (material == null || material.isEmpty()) return null;
        if (!mod.getItemStorage().hasItemInventoryOnly(material.getMatches())) {
            setDebugState("Collect smelt input " + material);
            return new CollectItemTask(material);
        }
        if (!hasFuel(mod)) {
            setDebugState("Collect fuel");
            return new CollectItemTask(new ItemTarget(Items.COAL, 1));
        }
        if (!StorageHelper.isSmokerOpen()) {
            return openOrPlaceSmoker(mod);
        }
        ItemStack output = StorageHelper.getItemStackInSlot(SmokerSlot.OUTPUT_SLOT);
        if (!output.isEmpty() && target.matches(output.getItem())) {
            setDebugState("Take smoker output");
            mod.getSlotHandler().clickSlot(SmokerSlot.OUTPUT_SLOT, 0, ClickType.QUICK_MOVE);
            return null;
        }
        if (StorageHelper.getItemStackInSlot(SmokerSlot.INPUT_SLOT_MATERIALS).isEmpty()) {
            quickMoveFirst(mod, material.getMatches());
            setDebugState("Insert smelt input");
            return null;
        }
        if (StorageHelper.getItemStackInSlot(SmokerSlot.INPUT_SLOT_FUEL).isEmpty()) {
            quickMoveFirst(mod, FUELS);
            setDebugState("Insert fuel");
            return null;
        }
        setDebugState("Wait for smoker");
        return null;
    }

    private Task openOrPlaceSmoker(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null || mod.getController() == null) return null;
        BlockPos smoker = findNearbySmoker(mod);
        if (smoker != null) {
            useBlock(mod, smoker, Direction.UP);
            setDebugState("Open smoker");
            return null;
        }
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.SMOKER)) {
            setDebugState("Collect smoker");
            return new CollectItemTask(new ItemTarget(Items.SMOKER, 1));
        }
        BlockPos placeAt = findPlaceSpot(mod);
        if (placeAt != null && mod.getSlotHandler().forceEquipItem(Items.SMOKER) && useCooldown-- <= 0) {
            useCooldown = 8;
            BlockPos support = placeAt.below();
            LookHelper.lookAt(mod, support);
            // Sneak while placing so interactive blocks (e.g. crafting table) don't intercept the click.
            mod.getInputControls().hold(Input.SNEAK);
            mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
            mod.getInputControls().release(Input.SNEAK);
            setDebugState("Place smoker");
        }
        return null;
    }

    private void useBlock(AltoClef mod, BlockPos pos, Direction direction) {
        if (useCooldown-- > 0) return;
        useCooldown = 8;
        LookHelper.lookAt(mod, pos);
        mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), direction, pos, false));
    }

    private BlockPos findNearbySmoker(AltoClef mod) {
        BlockPos center = mod.getPlayer().blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            if (mod.getWorld().getBlockState(pos).is(Blocks.SMOKER)) return pos.immutable();
        }
        return null;
    }

    private BlockPos findPlaceSpot(AltoClef mod) {
        BlockPos center = mod.getPlayer().blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
            if (mod.getWorld().getBlockState(pos).isAir() && mod.getWorld().getBlockState(pos.below()).isSolid()) {
                return pos.immutable();
            }
        }
        return null;
    }

    private void quickMoveFirst(AltoClef mod, Item... items) {
        for (Slot slot : mod.getItemStorage().getSlotsWithItemPlayerInventory(false, items)) {
            mod.getSlotHandler().clickSlot(slot, 0, ClickType.QUICK_MOVE);
            return;
        }
    }

    private boolean hasFuel(AltoClef mod) {
        return Arrays.stream(FUELS).anyMatch(item -> mod.getItemStorage().hasItemInventoryOnly(item));
    }

    private ItemTarget firstIngredient(RecipeHolder<?> holder) {
        for (Ingredient ingredient : holder.value().getIngredients()) {
            Item[] items = Arrays.stream(ingredient.getItems())
                    .filter(stack -> !stack.isEmpty())
                    .map(ItemStack::getItem)
                    .distinct()
                    .toArray(Item[]::new);
            if (items.length > 0) return new ItemTarget(items, target.getTargetCount());
        }
        return ItemTarget.EMPTY;
    }

    private RecipeHolder<?> findCookingRecipe(AltoClef mod) {
        if (mod.getPlayer() == null || Minecraft.getInstance().getConnection() == null) return null;
        RecipeHolder<?> fallback = null;
        for (RecipeHolder<?> holder : Minecraft.getInstance().getConnection().getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof AbstractCookingRecipe)) continue;
            ItemStack result = recipe.getResultItem(mod.getPlayer().registryAccess());
            if (result.isEmpty() || !target.matches(result.getItem())) continue;
            // Prefer the dedicated smoking recipe; fall back to any cooking recipe with the same output.
            if (recipe instanceof SmokingRecipe) return holder;
            fallback = holder;
        }
        return fallback;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.itemTargetsMetInventory(mod, target);
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SmeltInSmokerTask task && task.target.equals(target);
    }

    @Override
    protected String toDebugString() {
        return "Smoke " + target;
    }
}
