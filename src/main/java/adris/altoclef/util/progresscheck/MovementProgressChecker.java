package adris.altoclef.util.progresscheck;

import adris.altoclef.AltoClef;
import net.minecraft.world.phys.Vec3;

public class MovementProgressChecker {
    private Vec3 last = Vec3.ZERO;

    public MovementProgressChecker(double... ignored) {
    }

    public void reset() {
        last = Vec3.ZERO;
    }

    public boolean check(AltoClef mod) {
        if (mod.getPlayer() == null) return true;
        Vec3 now = mod.getPlayer().position();
        boolean moved = last == Vec3.ZERO || now.distanceToSqr(last) > 0.0025;
        last = now;
        return moved;
    }
}
