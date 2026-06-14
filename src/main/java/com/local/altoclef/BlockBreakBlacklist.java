package com.local.altoclef;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Persistent set of block IDs that AltoClef/Baritone must never mine.
 * Checked in WorldHelper.canBreak so Baritone treats them as unbreakable.
 */
public final class BlockBreakBlacklist {
    private static final Set<String> BLOCKED = new LinkedHashSet<>();
    private static boolean loaded = false;

    private BlockBreakBlacklist() {}

    public static boolean isBlocked(BlockState state) {
        ensureLoaded();
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return BLOCKED.contains(id);
    }

    public static boolean add(String id) {
        ensureLoaded();
        boolean changed = BLOCKED.add(normalise(id));
        if (changed) save();
        return changed;
    }

    public static boolean remove(String id) {
        ensureLoaded();
        boolean changed = BLOCKED.remove(normalise(id));
        if (changed) save();
        return changed;
    }

    public static Set<String> list() {
        ensureLoaded();
        return Collections.unmodifiableSet(BLOCKED);
    }

    // ── persistence ─────────────────────────────────────────────────────────

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path file = configFile();
        if (!Files.exists(file)) return;
        try {
            Files.lines(file)
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .map(BlockBreakBlacklist::normalise)
                .forEach(BLOCKED::add);
        } catch (IOException ignored) {}
    }

    private static void save() {
        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, BLOCKED);
        } catch (IOException ignored) {}
    }

    private static Path configFile() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("colossuscraft-block-blacklist.txt");
    }

    private static String normalise(String id) {
        // Add minecraft: prefix if namespace missing
        return id.contains(":") ? id : "minecraft:" + id;
    }
}
