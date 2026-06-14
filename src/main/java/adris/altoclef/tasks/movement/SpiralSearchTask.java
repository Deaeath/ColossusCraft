package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.ChunkScanCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * Searches for blocks using an expanding square spiral with mathematically
 * optimal track spacing.
 *
 * BlockTracker has a horizontal scan radius of SCAN_RADIUS blocks.  For a
 * circular sensor of radius R searching a plane, complete coverage with no
 * gaps or overlaps requires track spacing = 2R.  Waypoints are therefore
 * placed 2*SCAN_RADIUS blocks apart — visiting each "scan cell" exactly once.
 *
 * This is 6× more efficient than stepping chunk-by-chunk (16 blocks) when
 * the scan radius is 48 blocks.
 *
 * Spiral pattern (waypoint offsets in scan-cell units from origin):
 *   → 1, ↑ 1, ← 2, ↓ 2, → 3, ↑ 3 …
 * Each cell covers a square of (2R × 2R) blocks.
 */
public class SpiralSearchTask extends Task {

    // Must match BlockTracker's horizontal scan range (Settings.blockScanHorizontalRange).
    private static final int SCAN_RADIUS = 64;

    // Distance between spiral waypoints: 2 × scan radius = no overlap, no gap.
    private static final int STEP_BLOCKS = SCAN_RADIUS * 2; // 128 blocks

    // Arrive threshold: half a step so we don't overshoot the scan zone.
    private static final int ARRIVE_RADIUS_SQ = (STEP_BLOCKS / 2) * (STEP_BLOCKS / 2);

    // Advance to next waypoint if stuck here longer than this many ticks (~30s).
    private static final int STUCK_TIMEOUT_TICKS = 600;

    // If set, the spiral skips waypoints not in this biome (up to MAX_BIOME_SKIPS times before giving up).
    private static final int MAX_BIOME_SKIPS = 3;
    private final ResourceKey<Biome> _preferredBiome;

    // Optional: if set, spiral cells confirmed empty (CLEAN) are skipped automatically.
    // Each spiral step = STEP_BLOCKS wide = (STEP_BLOCKS/16) chunks per axis.
    private static final int HALF_CELL_CHUNKS = STEP_BLOCKS / 2 / 16; // 4 chunks
    private static final int MAX_CLEAN_SKIPS = 1000; // safety cap — stop infinite-looping if everything's clean
    private ChunkScanCache _chunkCache;

    private final int _originX;  // world-block origin
    private final int _originZ;
    private final int _targetY;

    // Spiral state — counts in scan-cell units (each cell = STEP_BLOCKS wide)
    private int _dx = 0, _dz = 0;
    private int _dirX = 1, _dirZ = 0;
    private int _step = 1;
    private int _stepCount = 0;
    private int _dirChanges = 0;

    private BlockPos _currentTarget;
    private int _gotoCooldown = 0;
    private int _stuckTicks = 0;

    /**
     * @param origin      start position (world coords)
     * @param targetY     Y level to travel at while searching
     * @param preferredBiome  optional — skip cells outside this biome
     * @param chunkCache  optional — skip cells already confirmed CLEAN
     */
    public SpiralSearchTask(BlockPos origin, int targetY, ResourceKey<Biome> preferredBiome, ChunkScanCache chunkCache) {
        _originX = origin.getX();
        _originZ = origin.getZ();
        _targetY = targetY;
        _preferredBiome = preferredBiome;
        _chunkCache = chunkCache;
    }

    public SpiralSearchTask(BlockPos origin, int targetY, ResourceKey<Biome> preferredBiome) {
        this(origin, targetY, preferredBiome, null);
    }

    public SpiralSearchTask(BlockPos origin, int targetY) {
        this(origin, targetY, null, null);
    }

    public SpiralSearchTask(BlockPos origin) {
        this(origin, origin.getY(), null, null);
    }

    public SpiralSearchTask(BlockPos origin, ResourceKey<Biome> preferredBiome) {
        this(origin, origin.getY(), preferredBiome, null);
    }

    private BlockPos advanceSpiral() {
        _dx += _dirX;
        _dz += _dirZ;
        _stepCount++;
        if (_stepCount >= _step) {
            _stepCount = 0;
            // Rotate 90° counter-clockwise: (x,z) → (-z, x)
            int tmp = _dirX;
            _dirX = -_dirZ;
            _dirZ = tmp;
            _dirChanges++;
            if (_dirChanges % 2 == 0) _step++;
        }
        return new BlockPos(
            _originX + _dx * STEP_BLOCKS,
            _targetY,
            _originZ + _dz * STEP_BLOCKS);
    }

    private boolean isCellClean(BlockPos center) {
        if (_chunkCache == null) return false;
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        return _chunkCache.isCellClean(cx - HALF_CELL_CHUNKS, cz - HALF_CELL_CHUNKS,
                                        cx + HALF_CELL_CHUNKS, cz + HALF_CELL_CHUNKS);
    }

    private BlockPos nextPreferredWaypoint(AltoClef mod) {
        // Skip cells that are confirmed empty AND (optionally) wrong biome.
        // Cap iterations so we don't infinite-loop if the whole world is scanned.
        int maxSkips = Math.max(MAX_BIOME_SKIPS, _chunkCache != null ? MAX_CLEAN_SKIPS : MAX_BIOME_SKIPS);
        for (int i = 0; i < maxSkips; i++) {
            BlockPos candidate = advanceSpiral();
            boolean biomeOk = _preferredBiome == null || mod.getWorld() == null
                    || mod.getWorld().getBiome(candidate).is(_preferredBiome);
            boolean notClean = !isCellClean(candidate);
            if (biomeOk && notClean) return candidate;
            // If only biome is wrong but we've exhausted normal skips, keep going (clean skip budget).
            // If only clean is wrong (cell has ore), we want it — biome check is secondary.
        }
        return advanceSpiral(); // safety: accept anything after cap
    }

    @Override
    protected void onStart(AltoClef mod) {
        _currentTarget = nextPreferredWaypoint(mod);
        _gotoCooldown = 0;
        _stuckTicks = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Vec3 pos = mod.getPlayer().position();
        double distSq = (_currentTarget.getX() - pos.x) * (_currentTarget.getX() - pos.x)
                      + (_currentTarget.getZ() - pos.z) * (_currentTarget.getZ() - pos.z);
        boolean arrived = distSq < ARRIVE_RADIUS_SQ;
        boolean stuck = ++_stuckTicks >= STUCK_TIMEOUT_TICKS;
        if (arrived || stuck) {
            _currentTarget = nextPreferredWaypoint(mod);
            _gotoCooldown = 0;
            _stuckTicks = 0;
        }

        if (_gotoCooldown-- <= 0) {
            _gotoCooldown = 40;
            mod.runBaritone("goto " + _currentTarget.getX() + " " + _targetY + " " + _currentTarget.getZ());
        }

        setDebugState("Spiral search cell (" + _dx + ", " + _dz + ") → "
            + _currentTarget.getX() + ", " + _targetY + ", " + _currentTarget.getZ());
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof SpiralSearchTask t) {
            return t._originX == _originX && t._originZ == _originZ && t._targetY == _targetY;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Spiral search";
    }
}
