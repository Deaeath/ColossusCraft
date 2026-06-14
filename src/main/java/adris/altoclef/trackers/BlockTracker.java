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

    // The full-volume rescan in updateState() is expensive (range^2 * height block lookups).
    // Throttle it: reuse the cached `known` map and only rescan when the tracked-block set
    // changes or a short interval elapses. Without this, every getKnownLocations() call (many
    // per tick) triggers a fresh ~600k-block scan and collapses the client frame rate.
    public static long SCAN_INTERVAL_MS = 3000;
    private long lastScanTimeMs = 0;
    private boolean hasScanned = false;
    private int lastTrackedHash = -1;

    private final ChunkScanCache chunkCache = new ChunkScanCache();

    public ChunkScanCache getChunkCache() { return chunkCache; }

    /** Returns the currently tracked block IDs as resource location strings. */
    public java.util.List<String> getTrackedBlockIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (Block b : trackingBlocks.keySet()) {
            net.minecraft.resources.ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
            if (key != null) ids.add(key.toString());
        }
        java.util.Collections.sort(ids);
        return ids;
    }

    public void forceRefresh() {
        hasScanned = false;
        lastScanTimeMs = 0L;
        known.clear();
        setDirty();
        updateState();
    }

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
        chunkCache.reset(); // tracked set changed — old clean marks are invalid
        setDirty();
    }

    public void stopTracking(Block... blocks) {
        for (Block block : blocks) {
            int count = trackingBlocks.getOrDefault(block, 0) - 1;
            if (count <= 0) trackingBlocks.remove(block);
            else trackingBlocks.put(block, count);
        }
        chunkCache.reset(); // tracked set changed — old clean marks are invalid
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
        // Use the throttled `known` cache instead of a fresh cubic scan. A brute-force
        // betweenClosed scan here is O(range^3): at range 64 that is ~2.1M getBlockState calls
        // EVERY tick (e.g. DefaultGoToDimensionTask polling for a portal), which collapses FPS.
        // The cache is populated by updateState() for tracked blocks; callers track what they query.
        updateState();
        BlockPos best = null;
        double bestDistance = range * range;
        for (Block block : blocks) {
            for (BlockPos candidate : known.getOrDefault(block, List.of())) {
                if (unreachable(candidate)) continue;
                double distance = candidate.getCenter().distanceToSqr(pos);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
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
        long now = System.currentTimeMillis();
        int trackedHash = trackingBlocks.keySet().hashCode();
        if (hasScanned && trackedHash == lastTrackedHash && now - lastScanTimeMs < SCAN_INTERVAL_MS) {
            return; // reuse cached `known`; the world hasn't been rescanned this interval
        }
        lastScanTimeMs = now;
        lastTrackedHash = trackedHash;
        hasScanned = true;
        known.clear();
        int horizontal = mod.getModSettings().getBlockScanHorizontalRange();
        int vertical = mod.getModSettings().getBlockScanVerticalRange();
        BlockPos center = mod.getPlayer().blockPosition();
        // Scan full range downward, only 16 blocks upward — ores are always below.
        BlockPos min = center.offset(-horizontal, -vertical, -horizontal);
        BlockPos max = center.offset(horizontal, 16, horizontal);
        Set<Block> tracked = trackingBlocks.keySet();
        for (BlockPos scan : BlockPos.betweenClosed(min, max)) {
            BlockPos pos = scan.immutable();
            Block block = mod.getWorld().getBlockState(pos).getBlock();
            if (tracked.contains(block) && !unreachable(pos)) {
                known.computeIfAbsent(block, ignored -> new ArrayList<>()).add(pos);
            }
        }

        // Mark chunk columns in the scan footprint as HAS_ORE or CLEAN.
        // This lets SpiralSearchTask skip confirmed-empty regions on future passes.
        Set<Long> chunksWithOre = new HashSet<>();
        for (List<BlockPos> positions : known.values()) {
            for (BlockPos pos : positions) {
                int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                if (chunksWithOre.add(key)) {
                    chunkCache.mark(cx, cz, ChunkScanCache.State.HAS_ORE);
                }
            }
        }
        int minCx = min.getX() >> 4, maxCx = max.getX() >> 4;
        int minCz = min.getZ() >> 4, maxCz = max.getZ() >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                if (!chunksWithOre.contains(key)) {
                    chunkCache.mark(cx, cz, ChunkScanCache.State.CLEAN);
                }
            }
        }
    }

    @Override
    protected void reset() {
        known.clear();
        unreachable.clear();
        hasScanned = false;
        lastTrackedHash = -1;
        chunkCache.reset();
    }
}
