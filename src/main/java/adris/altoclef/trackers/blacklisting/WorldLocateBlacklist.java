package adris.altoclef.trackers.blacklisting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class WorldLocateBlacklist extends AbstractObjectBlacklist<BlockPos> {
    @Override
    protected Vec3 getPos(BlockPos item) {
        return item.getCenter();
    }
}
