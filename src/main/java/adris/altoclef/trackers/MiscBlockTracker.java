package adris.altoclef.trackers;

import adris.altoclef.AltoClef;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MiscBlockTracker {

    private final AltoClef mod;
    private final Map<Dimension, BlockPos> lastNetherPortalsUsed = new HashMap<>();
    private Dimension lastDimension;
    private boolean newDimensionTriggered;

    public MiscBlockTracker(AltoClef mod) {
        this.mod = mod;
    }

    public void tick() {
        if (WorldHelper.getCurrentDimension() != lastDimension) {
            lastDimension = WorldHelper.getCurrentDimension();
            newDimensionTriggered = true;
        }
        if (AltoClef.inGame() && newDimensionTriggered) {
            BlockPos center = mod.getPlayer().blockPosition();
            for (BlockPos check : WorldHelper.scanRegion(mod, center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
                Block current = mod.getWorld().getBlockState(check).getBlock();
                if (current == Blocks.NETHER_PORTAL) {
                    BlockPos portal = check.immutable();
                    while (portal.getY() > mod.getWorld().getMinBuildHeight() && mod.getWorld().getBlockState(portal.below()).getBlock() == Blocks.NETHER_PORTAL) {
                        portal = portal.below();
                    }
                    if (WorldHelper.isSolid(mod, portal.below())) {
                        lastNetherPortalsUsed.put(WorldHelper.getCurrentDimension(), portal);
                        newDimensionTriggered = false;
                    }
                    break;
                }
            }
        }
    }

    public void reset() {
        lastNetherPortalsUsed.clear();
    }

    public Optional<BlockPos> getLastUsedNetherPortal(Dimension dimension) {
        BlockPos portal = lastNetherPortalsUsed.get(dimension);
        if (portal == null) return Optional.empty();
        if (mod.getChunkTracker().isChunkLoaded(portal) && !mod.getBlockTracker().blockIsValid(portal, Blocks.NETHER_PORTAL)) {
            lastNetherPortalsUsed.remove(dimension);
            return Optional.empty();
        }
        return Optional.of(portal);
    }
}
