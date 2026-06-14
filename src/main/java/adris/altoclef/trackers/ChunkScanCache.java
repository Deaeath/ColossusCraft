package adris.altoclef.trackers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks per-chunk-column scan state for the current tracked block set.
 * A chunk is CLEAN when a full scan pass covered it and found no tracked blocks.
 * Reset whenever the tracked block set changes so stale clean marks don't hide ore.
 */
public class ChunkScanCache {

    public enum State { UNKNOWN, CLEAN, HAS_ORE }

    private final Map<Long, State> cache = new HashMap<>();

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public State get(int cx, int cz) {
        return cache.getOrDefault(key(cx, cz), State.UNKNOWN);
    }

    public boolean isClean(int cx, int cz) {
        return cache.get(key(cx, cz)) == State.CLEAN;
    }

    public boolean isScanned(int cx, int cz) {
        return cache.containsKey(key(cx, cz));
    }

    public void mark(int cx, int cz, State state) {
        // Never downgrade HAS_ORE back to CLEAN (ore may still be there)
        if (state == State.CLEAN && cache.get(key(cx, cz)) == State.HAS_ORE) return;
        cache.put(key(cx, cz), state);
    }

    public void reset() {
        cache.clear();
    }

    public int countScanned() { return cache.size(); }

    public int countClean() {
        int n = 0;
        for (State s : cache.values()) if (s == State.CLEAN) n++;
        return n;
    }

    public int countHasOre() {
        int n = 0;
        for (State s : cache.values()) if (s == State.HAS_ORE) n++;
        return n;
    }

    /** Returns all entries as {cx, cz, stateOrdinal} for persistence. */
    public List<int[]> allEntries() {
        List<int[]> result = new ArrayList<>();
        for (Map.Entry<Long, State> e : cache.entrySet()) {
            long key = e.getKey();
            int cx = (int)(key >> 32);
            int cz = (int)(key & 0xFFFFFFFFL);
            result.add(new int[]{cx, cz, e.getValue().ordinal()});
        }
        return result;
    }

    /** Returns true if every chunk column in the rectangular cell is CLEAN. */
    public boolean isCellClean(int minCx, int minCz, int maxCx, int maxCz) {
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!isClean(cx, cz)) return false;
            }
        }
        return true;
    }
}
