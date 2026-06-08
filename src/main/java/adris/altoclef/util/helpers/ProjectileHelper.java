package adris.altoclef.util.helpers;

import net.minecraft.world.entity.projectile.Projectile;

public final class ProjectileHelper {
    public static final double ARROW_GRAVITY_ACCEL = 0.05;

    private ProjectileHelper() {
    }

    public static boolean hasGravity(Projectile projectile) {
        return !projectile.isNoGravity();
    }
}
