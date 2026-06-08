package adris.altoclef.util.helpers;

import net.minecraft.world.phys.Vec3;

public final class BaritoneHelper {
    public static final Object MINECRAFT_LOCK = new Object();

    private BaritoneHelper() {
    }

    public static double calculateGenericHeuristic(Vec3 start, Vec3 target) {
        return calculateGenericHeuristic(start.x, start.y, start.z, target.x, target.y, target.z);
    }

    public static double calculateGenericHeuristic(double xStart, double yStart, double zStart, double xTarget, double yTarget, double zTarget) {
        double dx = Math.abs(xTarget - xStart);
        double dy = Math.abs(yTarget - yStart);
        double dz = Math.abs(zTarget - zStart);
        return Math.max(dx, dz) + dy;
    }
}
