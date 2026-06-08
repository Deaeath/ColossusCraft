package adris.altoclef.util.helpers;

import adris.altoclef.AltoClef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class LookHelper {
    private LookHelper() {
    }

    public static void lookAt(AltoClef mod, Vec3 target) {
        if (mod.getPlayer() == null) return;
        Vec3 eye = mod.getPlayer().getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double xz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, xz));
        mod.getInputControls().forceLook(yaw, pitch);
    }

    public static void lookAt(AltoClef mod, BlockPos pos) {
        lookAt(mod, pos.getCenter());
    }
}
