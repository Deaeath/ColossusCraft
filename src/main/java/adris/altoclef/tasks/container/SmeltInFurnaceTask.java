package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.FurnaceSlot;
import adris.altoclef.util.slots.Slot;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

public class SmeltInFurnaceTask extends Task {
    private static final Item[] FUELS = {Items.COAL, Items.CHARCOAL, Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS};
    private final ItemTarget target;
    private int useCooldown;

    public SmeltInFurnaceTask(ItemTarget target) {
        this.target = target;
    }

    @Override
    protected void onStart(AltoClef mod) {
        useCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
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
        if (!StorageHelper.isFurnaceOpen()) {
            return openOrPlaceFurnace(mod);
        }
        ItemStack output = StorageHelper.getItemStackInSlot(FurnaceSlot.OUTPUT_SLOT);
        if (!output.isEmpty() && target.matches(output.getItem())) {
            setDebugState("Take furnace output");
            mod.getSlotHandler().clickSlot(FurnaceSlot.OUTPUT_SLOT, 0, ClickType.QUICK_MOVE);
            return null;
        }
        if (StorageHelper.getItemStackInSlot(FurnaceSlot.INPUT_SLOT_MATERIALS).isEmpty()) {
            quickMoveFirst(mod, material.getMatches());
            setDebugState("Insert smelt input");
            return null;
        }
        if (StorageHelper.getItemStackInSlot(FurnaceSlot.INPUT_SLOT_FUEL).isEmpty()) {
            quickMoveFirst(mod, FUELS);
            setDebugState("Insert fuel");
            return null;
        }
        setDebugState("Wait for furnace");
        return null;
    }

    private Task openOrPlaceFurnace(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null || mod.getController() == null) return null;
        BlockPos furnace = findNearbyFurnace(mod);
        if (furnace != null) {
            useBlock(mod, furnace, Direction.UP);
            setDebugState("Open furnace");
            return null;
        }
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.FURNACE)) {
            setDebugState("Collect furnace");
            return new CollectItemTask(new ItemTarget(Items.FURNACE, 1));
        }
        BlockPos placeAt = findPlaceSpot(mod);
        if (placeAt != null && mod.getSlotHandler().forceEquipItem(Items.FURNACE) && useCooldown-- <= 0) {
            useCooldown = 8;
            BlockPos support = placeAt.below();
            LookHelper.lookAt(mod, support);
            mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
            setDebugState("Place furnace");
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

    private BlockPos findNearbyFurnace(AltoClef mod) {
        BlockPos center = mod.getPlayer().blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            if (mod.getWorld().getBlockState(pos).is(Blocks.FURNACE)) return pos.immutable();
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
        for (RecipeHolder<?> holder : Minecraft.getInstance().getConnection().getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof AbstractCookingRecipe)) continue;
            ItemStack result = recipe.getResultItem(mod.getPlayer().registryAccess());
            if (!result.isEmpty() && target.matches(result.getItem())) return holder;
        }
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.itemTargetsMetInventory(mod, target);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SmeltInFurnaceTask task && task.target.equals(target);
    }

    @Override
    protected String toDebugString() {
        return "Smelt " + target;
    }
}
