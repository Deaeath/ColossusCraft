package adris.altoclef.util;

import adris.altoclef.Debug;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public enum MiningRequirement {
    HAND(Items.AIR),
    WOOD(Items.WOODEN_PICKAXE),
    STONE(Items.STONE_PICKAXE),
    IRON(Items.IRON_PICKAXE),
    DIAMOND(Items.DIAMOND_PICKAXE);

    private final Item minPickaxe;

    MiningRequirement(Item minPickaxe) {
        this.minPickaxe = minPickaxe;
    }

    public static MiningRequirement getMinimumRequirementForBlock(Block block) {
        if (block.defaultBlockState().requiresCorrectToolForDrops()) {
            for (MiningRequirement req : values()) {
                if (req == HAND) {
                    continue;
                }
                if (new ItemStack(req.getMinimumPickaxe()).isCorrectToolForDrops(block.defaultBlockState())) {
                    return req;
                }
            }
            Debug.logWarning("Failed to find effective tool against: " + block);
            return DIAMOND;
        }
        return HAND;
    }

    public Item getMinimumPickaxe() {
        return minPickaxe;
    }
}
