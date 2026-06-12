package adris.altoclef.util.helpers;

import adris.altoclef.util.baritone.CachedProjectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

public final class ProjectileHelper {
    public static final double ARROW_GRAVITY_ACCEL = 0.05000000074505806;
    public static final double THROWN_ENTITY_GRAVITY_ACCEL = 0.03;

    private ProjectileHelper() {
    }

    public static boolean hasGravity(Projectile projectile) {
        return !projectile.isNoGravity();
    }

    // If we shoot on a 2d plane, what is the 2d point on that trajectory closest to our player pos?
    private static Vec3 getClosestPointOnFlatLine(double shootX, double shootZ, double velX, double velZ, double playerX, double playerZ) {
        double deltaX = playerX - shootX,
                deltaZ = playerZ - shootZ;
        double t = ((velX * deltaX) + (velZ * deltaZ)) / (velX * velX + velZ * velZ);

        double hitX = shootX + velX * t,
                hitZ = shootZ + velZ * t;

        return new Vec3(hitX, 0, hitZ);
    }

    public static double getFlatDistanceSqr(double shootX, double shootZ, double velX, double velZ, double playerX, double playerZ) {
        return getClosestPointOnFlatLine(shootX, shootZ, velX, velZ, playerX, playerZ).distanceToSqr(playerX, 0, playerZ);
    }

    private static double getArrowHitHeight(double gravity, double horizontalVel, double verticalVel, double initialHeight, double distanceTraveled) {
        double time = distanceTraveled / horizontalVel;
        return initialHeight - (verticalVel * time) - 0.5 * (gravity * time * time);
    }

    /**
     * Calculates where we think an arrow will "hit" us, or at least where it will be at its closest.
     */
    public static Vec3 calculateArrowClosestApproach(Vec3 shootOrigin, Vec3 shootVelocity, double yGravity, Vec3 playerOrigin) {
        Vec3 flatEncounter = getClosestPointOnFlatLine(shootOrigin.x, shootOrigin.z, shootVelocity.x, shootVelocity.z, playerOrigin.x, playerOrigin.z);
        double encounterDistanceTraveled = (flatEncounter.subtract(shootOrigin.x, flatEncounter.y, shootOrigin.z)).length();

        double horizontalVel = Math.sqrt(shootVelocity.x * shootVelocity.x + shootVelocity.z * shootVelocity.z);
        double verticalVel = shootVelocity.y;
        double initialHeight = shootOrigin.y;

        double hitHeight = getArrowHitHeight(yGravity, horizontalVel, verticalVel, initialHeight, encounterDistanceTraveled);

        return new Vec3(flatEncounter.x, hitHeight, flatEncounter.z);
    }

    public static Vec3 calculateArrowClosestApproach(CachedProjectile projectile, Vec3 pos) {
        return calculateArrowClosestApproach(projectile.position, projectile.velocity, projectile.gravity, pos);
    }

    public static Vec3 calculateArrowClosestApproach(CachedProjectile projectile, Player player) {
        return calculateArrowClosestApproach(projectile, player.position());
    }
}
