package adris.altoclef.trackers;

import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Persists ChunkScanCache to disk so scanned-chunk state survives between sessions.
 *
 * File layout: <gameDir>/colossuscraft/<context>/<dimPath>_chunkcache.txt
 *   context = sanitized server IP, or "local" for singleplayer
 *   dimPath = e.g. "mining"
 *
 * File format: one line per chunk → "STATE cx cz"
 * First line: "# blocks tracked: <comma-separated block IDs>"
 * If tracked blocks differ from what's in the cache file, the file is discarded.
 */
public final class ChunkCachePersistence {

    private ChunkCachePersistence() {}

    // ── public API ──────────────────────────────────────────────────────────

    public static void save(ChunkScanCache cache, String dimensionPath, Collection<String> trackedBlockIds) {
        Path file = resolveFile(dimensionPath);
        if (file == null || cache.countScanned() == 0) return;
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file)) {
                w.write("# blocks: " + String.join(",", sorted(trackedBlockIds)));
                w.newLine();
                for (int[] entry : cache.allEntries()) {
                    // entry = {cx, cz, stateOrdinal}
                    w.write(ChunkScanCache.State.values()[entry[2]].name() + " " + entry[0] + " " + entry[1]);
                    w.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("[ChunkCachePersistence] save failed: " + e.getMessage());
        }
    }

    public static void load(ChunkScanCache cache, String dimensionPath, Collection<String> trackedBlockIds) {
        Path file = resolveFile(dimensionPath);
        if (file == null || !Files.exists(file)) return;
        try (BufferedReader r = Files.newBufferedReader(file)) {
            String header = r.readLine();
            if (header == null) return;
            // Validate tracked block set
            String expectedBlocks = "# blocks: " + String.join(",", sorted(trackedBlockIds));
            if (!header.equals(expectedBlocks)) {
                System.out.println("[ChunkCachePersistence] tracked blocks changed — discarding stale cache");
                Files.deleteIfExists(file);
                return;
            }
            cache.reset();
            String line;
            int loaded = 0;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(" ");
                if (parts.length < 3) continue;
                try {
                    ChunkScanCache.State state = ChunkScanCache.State.valueOf(parts[0]);
                    int cx = Integer.parseInt(parts[1]);
                    int cz = Integer.parseInt(parts[2]);
                    cache.mark(cx, cz, state);
                    loaded++;
                } catch (Exception ignored) {}
            }
            System.out.println("[ChunkCachePersistence] loaded " + loaded + " chunks from " + file.getFileName());
        } catch (IOException e) {
            System.err.println("[ChunkCachePersistence] load failed: " + e.getMessage());
        }
    }

    // ── internals ───────────────────────────────────────────────────────────

    private static Path resolveFile(String dimensionPath) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameDirectory == null) return null;
        String context = resolveContext(mc);
        String dimKey = dimensionPath.replace(":", "_").replace("/", "_");
        return mc.gameDirectory.toPath()
            .resolve("colossuscraft")
            .resolve(context)
            .resolve(dimKey + "_chunkcache.txt");
    }

    private static String resolveContext(Minecraft mc) {
        if (mc.getSingleplayerServer() != null) {
            return "local_" + sanitize(mc.getSingleplayerServer().getWorldData().getLevelName());
        }
        if (mc.getCurrentServer() != null) {
            return "server_" + sanitize(mc.getCurrentServer().ip);
        }
        return "unknown";
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase(java.util.Locale.ROOT);
    }

    private static List<String> sorted(Collection<String> ids) {
        List<String> list = new ArrayList<>(ids);
        Collections.sort(list);
        return list;
    }
}
