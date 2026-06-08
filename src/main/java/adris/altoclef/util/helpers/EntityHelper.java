package adris.altoclef.util.helpers;

import adris.altoclef.AltoClef;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

public final class EntityHelper {
    private EntityHelper() {
    }

    public static boolean isAngryAtPlayer(AltoClef mod, Entity entity) {
        if (mod.getPlayer() == null) return false;
        if (entity instanceof Enemy) return entity.distanceTo(mod.getPlayer()) < 26;
        if (entity instanceof Mob mob && mob.getTarget() != null) return mob.getTarget().equals(mod.getPlayer());
        return false;
    }
}
