package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
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

    // Must match BlockTracker's horizontal scan range.
    private static final int SCAN_RADIUS = 48;

    // Distance between spiral waypoints: 2 × scan radius = no overlap, no gap.
    private static final int STEP_BLOCKS = SCAN_RADIUS * 2; // 96 blocks

    // Arrive threshold: half a step so we don't overshoot the scan zone.
    private static final int ARRIVE_RADIUS_SQ = (STEP_BLOCKS / 2) * (STEP_BLOCKS / 2);

    // If set, the spiral skips waypoints not in this biome (up to MAX_BIOME_SKIPS times before giving up).
    private static final int MAX_BIOME_SKIPS = 3;
    private final ResourceKey<Biome> _preferredBiome;

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

    /**
     * @param origin  start position (world coords)
     * @param targetY Y level to travel at while searching (use surface Y for
     *                surface blocks, ore Y for underground blocks)
     */
    public SpiralSearchTask(BlockPos origin, int targetY, ResourceKey<Biome> preferredBiome) {
        _originX = origin.getX();
        _originZ = origin.getZ();
        _targetY = targetY;
        _preferredBiome = preferredBiome;
    }

    public SpiralSearchTask(BlockPos origin, int targetY) {
        this(origin, targetY, null);
    }

    public SpiralSearchTask(BlockPos origin) {
        this(origin, origin.getY(), null);
    }

    public SpiralSearchTask(BlockPos origin, ResourceKey<Biome> preferredBiome) {
        this(origin, origin.getY(), preferredBiome);
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

    private BlockPos nextPreferredWaypoint(AltoClef mod) {
        if (_preferredBiome == null || mod.getWorld() == null) return advanceSpiral();
        for (int i = 0; i < MAX_BIOME_SKIPS; i++) {
            BlockPos candidate = advanceSpiral();
            Holder<Biome> biome = mod.getWorld().getBiome(candidate);
            if (biome.is(_preferredBiome)) return candidate;
        }
        return advanceSpiral(); // give up, go wherever
    }

    @Override
    protected void onStart(AltoClef mod) {
        _currentTarget = nextPreferredWaypoint(mod);
        _gotoCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Vec3 pos = mod.getPlayer().position();
        double distSq = (_currentTarget.getX() - pos.x) * (_currentTarget.getX() - pos.x)
                      + (_currentTarget.getZ() - pos.z) * (_currentTarget.getZ() - pos.z);
        if (distSq < ARRIVE_RADIUS_SQ) {
            _currentTarget = nextPreferredWaypoint(mod);
            _gotoCooldown = 0;
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
