package adris.altoclef.tasks.resources;

import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

import java.util.function.Function;

public abstract class CraftWithMatchingWoolTask extends CraftWithMatchingMaterialsTask {
    public CraftWithMatchingWoolTask(ItemTarget target, Function<?, Item> getMajorityMaterial, Function<?, Item> getTargetItem, CraftingRecipe recipe, boolean[] sameMask) {
        super(target, recipe, sameMask);
    }
}
