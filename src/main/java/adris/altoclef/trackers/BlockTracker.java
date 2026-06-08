package adris.altoclef.trackers;

import adris.altoclef.AltoClef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class BlockTracker extends Tracker {

    private final AltoClef mod;
    private final Map<Block, Integer> trackingBlocks = new HashMap<>();
    private final Map<Block, List<BlockPos>> known = new HashMap<>();
    private final Set<BlockPos> unreachable = new HashSet<>();

    public BlockTracker(AltoClef mod, TrackerManager manager) {
        super(manager);
        this.mod = mod;
    }

    public void preTickTask() {
    }

    public void postTickTask() {
    }

    public boolean isTracking(Block block) {
        return trackingBlocks.getOrDefault(block, 0) > 0;
    }

    public void trackBlock(Block... blocks) {
        for (Block block : blocks) {
            trackingBlocks.put(block, trackingBlocks.getOrDefault(block, 0) + 1);
        }
        setDirty();
    }

    public void stopTracking(Block... blocks) {
        for (Block block : blocks) {
            int count = trackingBlocks.getOrDefault(block, 0) - 1;
            if (count <= 0) trackingBlocks.remove(block);
            else trackingBlocks.put(block, count);
        }
        setDirty();
    }

    public void addBlock(Block block, BlockPos pos) {
        if (blockIsValid(pos, block)) {
            known.computeIfAbsent(block, ignored -> new ArrayList<>()).add(pos.immutable());
        }
    }

    public boolean anyFound(Block... blocks) {
        updateState();
        for (Block block : blocks) {
            if (!getKnownLocations(block).isEmpty()) return true;
        }
        return false;
    }

    public boolean anyFound(Predicate<BlockPos> isValidTest, Block... blocks) {
        updateState();
        for (Block block : blocks) {
            for (BlockPos pos : getKnownLocations(block)) {
                if (isValidTest.test(pos)) return true;
            }
        }
        return false;
    }

    public Optional<BlockPos> getNearestTracking(Block... blocks) {
        return mod.getPlayer() == null ? Optional.empty() : getNearestTracking(mod.getPlayer().position().add(0, 0.6, 0), blocks);
    }

    public Optional<BlockPos> getNearestTracking(Vec3 pos, Block... blocks) {
        return getNearestTracking(pos, p -> true, blocks);
    }

    public Optional<BlockPos> getNearestTracking(Predicate<BlockPos> isValidTest, Block... blocks) {
        return mod.getPlayer() == null ? Optional.empty() : getNearestTracking(mod.getPlayer().position().add(0, 0.6, 0), isValidTest, blocks);
    }

    public Optional<BlockPos> getNearestTracking(Vec3 pos, Predicate<BlockPos> isValidTest, Block... blocks) {
        updateState();
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Block block : blocks) {
            for (BlockPos candidate : getKnownLocations(block)) {
                if (unreachable(candidate) || !isValidTest.test(candidate) || !blockIsValid(candidate, block)) continue;
                double distance = candidate.getCenter().distanceToSqr(pos);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public List<BlockPos> getKnownLocations(Block... blocks) {
        updateState();
        List<BlockPos> result = new ArrayList<>();
        for (Block block : blocks) {
            result.addAll(known.getOrDefault(block, List.of()));
        }
        return result;
    }

    public Optional<BlockPos> getNearestWithinRange(BlockPos pos, double range, Block... blocks) {
        return getNearestWithinRange(pos.getCenter(), range, blocks);
    }

    public Optional<BlockPos> getNearestWithinRange(Vec3 pos, double range, Block... blocks) {
        BlockPos center = BlockPos.containing(pos);
        int r = (int) Math.ceil(range);
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (BlockPos check : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            BlockPos immutable = check.immutable();
            if (unreachable(immutable) || !blockIsValid(immutable, blocks)) continue;
            double distance = immutable.getCenter().distanceToSqr(pos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = immutable;
            }
        }
        return Optional.ofNullable(best);
    }

    public boolean blockIsValid(BlockPos pos, Block... blocks) {
        if (mod.getWorld() == null || pos == null) return false;
        Block current = mod.getWorld().getBlockState(pos).getBlock();
        for (Block block : blocks) {
            if (current == block) return true;
        }
        return false;
    }

    public void requestBlockUnreachable(BlockPos pos) {
        requestBlockUnreachable(pos, 1);
    }

    public void requestBlockUnreachable(BlockPos pos, int penalty) {
        if (pos != null) unreachable.add(pos.immutable());
    }

    public boolean unreachable(BlockPos pos) {
        return pos != null && unreachable.contains(pos.immutable());
    }

    @Override
    protected void updateState() {
        if (mod.getPlayer() == null || mod.getWorld() == null || trackingBlocks.isEmpty()) return;
        known.clear();
        int horizontal = mod.getModSettings().getBlockScanHorizontalRange();
        int vertical = mod.getModSettings().getBlockScanVerticalRange();
        BlockPos center = mod.getPlayer().blockPosition();
        BlockPos min = center.offset(-horizontal, -vertical, -horizontal);
        BlockPos max = center.offset(horizontal, vertical, horizontal);
        Set<Block> tracked = trackingBlocks.keySet();
        for (BlockPos scan : BlockPos.betweenClosed(min, max)) {
            BlockPos pos = scan.immutable();
            Block block = mod.getWorld().getBlockState(pos).getBlock();
            if (tracked.contains(block) && !unreachable(pos)) {
                known.computeIfAbsent(block, ignored -> new ArrayList<>()).add(pos);
            }
        }
    }

    @Override
    protected void reset() {
        known.clear();
        unreachable.clear();
    }
}
