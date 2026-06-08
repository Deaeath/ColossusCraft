package adris.altoclef.util.baritone;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class CachedProjectile {
    public Vec3 position = Vec3.ZERO;
    public Vec3 velocity = Vec3.ZERO;
    public double gravity;
    public Class<? extends Entity> projectileType;
}
