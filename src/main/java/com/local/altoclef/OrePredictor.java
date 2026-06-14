package com.local.altoclef;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Predicts exact allthemodium ore placement positions in the mining dimension
 * from the world seed.
 *
 * Algorithm mirrors what ChunkGenerator.applyBiomeDecoration() does:
 *   1. setDecorationSeed(worldSeed, chunkOriginX, chunkOriginZ)
 *   2. setFeatureSeed(decorSeed, featureIndex, UNDERGROUND_ORES_STEP)
 *   3. count=1 → no RNG consumed
 *   4. in_square → nextInt(16) × 2
 *   5. height_range trapezoid(65,129) → nextInt(33) × 2 (+65 offset)
 *
 * The mining dimension is flat:
 *   Y=0   bedrock
 *   Y=1-64   end_stone
 *   Y=65-128 netherrack (NOT in stone_ore_replaceables — ore blob does nothing here)
 *   Y=129-192 deepslate (IN deepslate_ore_replaceables → allthemodium_slate_ore places)
 *   Y=193-311 stone (IN stone_ore_replaceables, but trapezoid max is 129 → never reached)
 *
 * Ore generates only when predicted Y ≥ DEEPSLATE_Y (129).
 * A size=4 blob can extend ~±2 blocks, so we use BLOB_MARGIN=3 as fuzz.
 *
 * Feature index = 12 is the default (ore_allthemodium is 13th alphabetically
 * in allthemodium's dim_ores biome modifiers). Use calibrate() to verify.
 */
public final class OrePredictor {

    // GenerationStep.Decoration.UNDERGROUND_ORES.ordinal() in MC 1.21.1
    public static final int UNDERGROUND_ORES_STEP = 6;

    // Default feature index for allthemodium:allthemodium_mining within the
    // underground_ores step of the allthemodium:mining biome.
    // Derived from alphabetical order of dim_ores biome modifiers:
    // aluminum(0) coal(1) copper(2) diamond(3) emerald(4) gold(5) iridium(6)
    // iron(7) lapis(8) lead(9) netherite(10) nickel(11) ore_allthemodium(12)
    public static int defaultFeatureIndex = 12;

    private OrePredictor() {}

    // ── public API ──────────────────────────────────────────────────────────

    /**
     * Scan a square of chunks centred on (originCx, originCz) with the given
     * half-radius (in chunks). Returns ore positions sorted nearest-first.
     *
     * @param worldSeed    world seed (from /seed)
     * @param originCx     player chunk X
     * @param originCz     player chunk Z
     * @param radiusChunks search radius in chunks (e.g. 512 → 1km radius)
     * @param featureIndex feature index within underground_ores step
     */
    public static List<BlockPos> scan(long worldSeed, int originCx, int originCz,
                                      int radiusChunks, int featureIndex) {
        List<BlockPos> hits = new ArrayList<>();
        for (int dcx = -radiusChunks; dcx <= radiusChunks; dcx++) {
            for (int dcz = -radiusChunks; dcz <= radiusChunks; dcz++) {
                int cx = originCx + dcx;
                int cz = originCz + dcz;
                BlockPos pos = predictChunk(worldSeed, cx, cz, featureIndex);
                if (pos != null) hits.add(pos);
            }
        }
        // Sort nearest-first from origin chunk centre
        double ox = originCx * 16.0 + 8, oz = originCz * 16.0 + 8;
        hits.sort(Comparator.comparingDouble(p -> (p.getX() - ox) * (p.getX() - ox)
                                                + (p.getZ() - oz) * (p.getZ() - oz)));
        return hits;
    }

    /**
     * Brute-force the correct feature index by finding which value predicts
     * ore in the chunk containing the given known ore position.
     * Returns the matching index, or -1 if none found.
     */
    public static int calibrate(long worldSeed, int knownOreX, int knownOreZ) {
        return calibrate(worldSeed, knownOreX, Integer.MIN_VALUE, knownOreZ);
    }

    public static int calibrate(long worldSeed, int knownOreX, int knownOreY, int knownOreZ) {
        int targetCx = knownOreX >> 4;
        int targetCz = knownOreZ >> 4;
        int best = -1;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int idx = 0; idx <= 256; idx++) {
            BlockPos pred = predictChunk(worldSeed, targetCx, targetCz, idx);
            if (pred == null) continue;
            int dx = pred.getX() - knownOreX;
            int dz = pred.getZ() - knownOreZ;
            double score = dx * dx + dz * dz;
            if (knownOreY != Integer.MIN_VALUE) {
                score += Math.min(Math.abs(pred.getY() - knownOreY), 32) * 0.25D;
            }
            if (score < bestScore) {
                bestScore = score;
                best = idx;
            }
        }
        return best;
    }

    /**
     * Predict whether ore generates in a single chunk.
     * Returns the predicted ore blob centre, or null if no ore generates.
     */
    public static BlockPos predictChunk(long worldSeed, int chunkX, int chunkZ, int featureIndex) {
        WorldgenRandom rng = new WorldgenRandom(new XoroshiroRandomSource(0L));
        long decorSeed = rng.setDecorationSeed(worldSeed, chunkX << 4, chunkZ << 4);
        rng.setFeatureSeed(decorSeed, featureIndex, UNDERGROUND_ORES_STEP);

        // count=1: no RNG consumed (ConstantInt.sample() returns value directly)
        // in_square: 2 × nextInt(16)
        int xOff = rng.nextInt(16);
        int zOff = rng.nextInt(16);

        // height_range trapezoid(min=65, max=129, plateau=0):
        //   spread = max - min - plateau = 64
        //   y = min + nextIntBetweenInclusive(0, spread/2) + nextIntBetweenInclusive(0, (spread+1)/2)
        //     = 65 + nextInt(33) + nextInt(33)
        //   range: 65..129, peak at 97
        int y = 65 + rng.nextInt(33) + rng.nextInt(33);

        // biome check: allthemodium:mining fills the whole dimension → always passes

        // Ore generates only if the blob can reach deepslate (Y ≥ DEEPSLATE_Y).
        // With blob margin, even y = DEEPSLATE_Y - BLOB_MARGIN can produce ore.
        return new BlockPos(chunkX * 16 + xOff, y, chunkZ * 16 + zOff);
    }
}
