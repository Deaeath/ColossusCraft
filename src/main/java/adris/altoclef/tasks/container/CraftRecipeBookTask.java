package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

public class CraftRecipeBookTask extends Task {
    private final ItemTarget target;
    private final boolean craftAll;
    private final RecipeHolder<?> recipe;
    private int delay;
    private int resultSlot = -1;
    private int menuId = -1;
    private boolean sent;
    private boolean done;

    public CraftRecipeBookTask(ItemTarget target, boolean craftAll) {
        this(target, craftAll, null);
    }

    public CraftRecipeBookTask(ItemTarget target, boolean craftAll, RecipeHolder<?> recipe) {
        this.target = target;
        this.craftAll = craftAll;
        this.recipe = recipe;
    }

    @Override
    protected void onStart(AltoClef mod) {
        delay = 0;
        resultSlot = -1;
        menuId = -1;
        sent = false;
        done = false;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || mc.gameMode == null) {
            done = true;
            return null;
        }
        if (!sent) {
            if (!(player.containerMenu instanceof RecipeBookMenu<?, ?> menu)) {
                mod.log("Open inventory or crafting table for recipe-book craft.");
                done = true;
                return null;
            }
            RecipeHolder<?> holder = findRecipe(player);
            if (holder == null) {
                mod.log("No recipe for " + target);
                done = true;
                return null;
            }
            mc.getConnection().getConnection().send(new ServerboundPlaceRecipePacket(menu.containerId, holder, craftAll));
            menuId = menu.containerId;
            resultSlot = menu.getResultSlotIndex();
            delay = 6;
            sent = true;
            setDebugState("Filling recipe " + target);
            return null;
        }
        if (delay-- > 0) return null;
        if (player.containerMenu.containerId == menuId && resultSlot >= 0) {
            mc.gameMode.handleInventoryMouseClick(menuId, resultSlot, 0, ClickType.QUICK_MOVE, player);
        }
        done = true;
        return null;
    }

    private RecipeHolder<?> findRecipe(LocalPlayer player) {
        if (recipe != null) {
            return recipe;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;
        for (RecipeHolder<?> holder : mc.getConnection().getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof CraftingRecipe)) continue;
            ItemStack result = recipe.getResultItem(player.registryAccess());
            if (!result.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
                for (Item item : target.getMatches()) {
                    if (id != null && BuiltInRegistries.ITEM.getKey(item).equals(id)) {
                        return holder;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return done;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CraftRecipeBookTask task && task.target.equals(target) && task.craftAll == craftAll;
    }

    @Override
    protected String toDebugString() {
        return "Craft " + target;
    }
}
