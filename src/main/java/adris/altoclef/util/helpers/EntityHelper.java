package adris.altoclef.util.helpers;

import adris.altoclef.AltoClef;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class EntityHelper {
    private EntityHelper() {
    }

    public static boolean isAngryAtPlayer(AltoClef mod, Entity entity) {
        if (mod.getPlayer() == null) return false;
        if (isActivelyTargetingPlayer(mod, entity)) return true;
        if (entity instanceof Enemy) return entity.distanceTo(mod.getPlayer()) < 26;
        return false;
    }

    public static boolean isActivelyTargetingPlayer(AltoClef mod, Entity entity) {
        return mod.getPlayer() != null
                && entity instanceof Mob mob
                && mob.getTarget() != null
                && mob.getTarget().equals(mod.getPlayer());
    }

    public static boolean isGenerallyHostileToPlayer(AltoClef mod, Entity hostile) {
        if (hostile instanceof Shulker) return true;
        // This is only temporary.
        if (hostile instanceof Mob entity) {
            if (entity instanceof Monster monster) {
                return monster.isAggressive() || !(monster instanceof EnderMan || monster instanceof Piglin ||
                        monster instanceof Spider || monster instanceof ZombifiedPiglin);
            }
            if (entity instanceof Slime slime) {
                return slime.hasLineOfSight(mod.getPlayer());
            }
            return entity.isAggressive();
        }
        return !isTradingPiglin(hostile);
    }

    public static boolean isTradingPiglin(Entity entity) {
        if (entity instanceof Piglin pig) {
            for (ItemStack stack : pig.getHandSlots()) {
                if (stack.getItem().equals(Items.GOLD_INGOT)) {
                    // We're trading with this one, ignore it.
                    return true;
                }
            }
        }
        return false;
    }
}
