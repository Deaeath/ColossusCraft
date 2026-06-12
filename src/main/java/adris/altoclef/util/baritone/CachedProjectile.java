package adris.altoclef.util.baritone;

import adris.altoclef.util.time.TimerGame;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class CachedProjectile {
    private final TimerGame _lastCache = new TimerGame(2);
    public Vec3 position = Vec3.ZERO;
    public Vec3 velocity = Vec3.ZERO;
    public double gravity;
    public Class<? extends Entity> projectileType;
    private Vec3 _cachedHit;
    private boolean _cacheHeld = false;

    public Vec3 getCachedHit() {
        return _cachedHit;
    }

    public void setCacheHit(Vec3 cache) {
        _cachedHit = cache;
        _cacheHeld = true;
        _lastCache.reset();
    }

    public boolean needsToRecache() {
        return !_cacheHeld || _lastCache.elapsed();
    }
}
