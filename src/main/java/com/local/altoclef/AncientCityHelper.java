package com.local.altoclef;

import adris.altoclef.platform.NeoForgeAltoClefMod;
import baritone.api.BaritoneAPI;
import baritone.api.utils.input.Input;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * Sneak + warden avoidance for deep dark / ancient city navigation.
 *
 * Auto-sneak: enabled while in the deep_dark biome (or manual override).
 * Warden freeze: stops pathing and holds sneak when a nearby warden has
 *   anger >= FREEZE_ANGER and is not yet targeting the player.
 * Warden flee: handled by MobDefenseChain (Warden is already in getUniversallyDangerousMob).
 */
public final class AncientCityHelper {
    private static final int FREEZE_ANGER = 70;   // warden anger level that triggers freeze
    private static final int FREEZE_RANGE = 24;   // blocks — don't react to distant wardens
    private static final int SCULK_SCAN_RANGE = 20;

    private static boolean initialized;
    private static boolean manualSneak = false;   // /cc sneak on forces it regardless of biome
    private static boolean frozen = false;         // currently frozen waiting for warden to calm

    private AncientCityHelper() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener(AncientCityHelper::clientTick);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> sneakCommand() {
        return Commands.literal("sneak")
                .then(Commands.literal("on").executes(ctx -> setSneak(true)))
                .then(Commands.literal("off").executes(ctx -> setSneak(false)))
                .then(Commands.literal("status").executes(ctx -> status()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> mineCommand() {
        return Commands.literal("mine")
                .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                        .then(Commands.argument("blocks", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> {
                                    String rem = builder.getRemaining().toLowerCase();
                                    // Suggest based on the last space-separated token
                                    int lastSpace = rem.lastIndexOf(' ');
                                    String prefix = lastSpace >= 0 ? rem.substring(0, lastSpace + 1) : "";
                                    String token = lastSpace >= 0 ? rem.substring(lastSpace + 1) : rem;
                                    BuiltInRegistries.BLOCK.keySet().stream()
                                            .map(ResourceLocation::toString)
                                            .filter(s -> s.contains(token))
                                            .sorted()
                                            .limit(200)
                                            .forEach(s -> builder.suggest(prefix + s));
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> startMine(
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "blocks"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.argument("blocks", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            String rem = builder.getRemaining().toLowerCase();
                            int lastSpace = rem.lastIndexOf(' ');
                            String prefix = lastSpace >= 0 ? rem.substring(0, lastSpace + 1) : "";
                            String token = lastSpace >= 0 ? rem.substring(lastSpace + 1) : rem;
                            BuiltInRegistries.BLOCK.keySet().stream()
                                    .map(ResourceLocation::toString)
                                    .filter(s -> s.contains(token))
                                    .sorted()
                                    .limit(200)
                                    .forEach(s -> builder.suggest(prefix + s));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> startMine(
                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "blocks"), 1)));
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        boolean inDeepDark = mc.level.getBiome(player.blockPosition()).is(Biomes.DEEP_DARK);
        boolean shouldSneak = manualSneak || inDeepDark;

        // Warden awareness — freeze only while warden is building anger toward the player.
        // Once it locks on (or we've teleported away), unfreeze immediately.
        Warden angryWarden = nearbyAngryWarden(mc, player);
        boolean wardenBuilding = angryWarden != null
                && angryWarden.getClientAngerLevel() >= FREEZE_ANGER
                && (angryWarden.getTarget() == null || !angryWarden.getTarget().equals(player));
        if (wardenBuilding) {
            if (!frozen) {
                frozen = true;
                try {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                } catch (Throwable ignored) {}
                say("Warden nearby — holding still (anger " + angryWarden.getClientAngerLevel() + ")");
            }
            applySneak(true);
            return;
        } else if (frozen) {
            frozen = false;
            applySneak(false);  // explicit release — don't rely on fall-through
            say("Warden calmed — resuming");
        }

        applySneak(shouldSneak);
    }

    /** Force sneak at both the MC key-binding level and Baritone's input override. */
    private static void applySneak(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyShift.setDown(on);
        try {
            var bar = BaritoneAPI.getProvider().getPrimaryBaritone();
            bar.getInputOverrideHandler().setInputForceState(Input.SNEAK, on);
            // Tell Baritone's pathfinder not to sprint — setInputForceState is re-overridden every tick.
            BaritoneAPI.getSettings().allowSprint.value = !on;
        } catch (Throwable ignored) {}
    }

    private static Warden nearbyAngryWarden(Minecraft mc, LocalPlayer player) {
        Warden worst = null;
        int worstAnger = 0;
        try {
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity instanceof Warden w && w.isAlive()
                        && entity.distanceTo(player) <= FREEZE_RANGE
                        && w.getClientAngerLevel() > worstAnger) {
                    worst = w;
                    worstAnger = w.getClientAngerLevel();
                }
            }
        } catch (Exception ignored) {}
        return worst;
    }

    private static int startMine(String input, int count) {
        // Accept space or comma separated list: "allthemodium:allthemodium_ore minecraft:diamond_ore"
        String[] tokens = input.trim().split("[\\s,]+");
        List<Block> blocks = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> unknown = new ArrayList<>();

        for (String token : tokens) {
            if (token.isBlank()) continue;
            String full = token.contains(":") ? token : "minecraft:" + token;
            Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(full)).orElse(null);
            if (block != null) {
                blocks.add(block);
                names.add(token);
            } else {
                // Mod block not in registry by that key — fall back to mineByName for this one
                names.add(token);
                unknown.add(token);
            }
        }

        if (blocks.isEmpty() && unknown.isEmpty()) {
            say("No valid blocks specified.");
            return 0;
        }

        try {
            var mine = BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess();
            if (!unknown.isEmpty()) {
                // Use mineByName for the whole list (handles mod blocks by path)
                mine.mineByName(count, names.toArray(new String[0]));
            } else {
                mine.mine(count, blocks.toArray(new Block[0]));
            }
            say("Mining: " + String.join(", ", names) + (count > 1 ? " x" + count : ""));
        } catch (Throwable e) {
            say("Mine failed: " + e.getMessage());
            return 0;
        }
        return 1;
    }

    /** Programmatic sneak control for tasks (does not print a message). */
    public static void setManualSneak(boolean on) {
        manualSneak = on;
        applySneak(on);
    }

    private static int setSneak(boolean on) {
        manualSneak = on;
        applySneak(on);
        say("Sneak mode: " + (on ? "ON" : "OFF"));
        return 1;
    }

    private static int status() {
        Minecraft mc = Minecraft.getInstance();
        boolean inDeepDark = mc.player != null && mc.level != null
                && mc.level.getBiome(mc.player.blockPosition()).is(Biomes.DEEP_DARK);
        Warden w = mc.player != null ? nearbyAngryWarden(mc, mc.player) : null;
        say("Sneak: manual=" + manualSneak + " deepDark=" + inDeepDark + " frozen=" + frozen
                + (w != null ? " warden-anger=" + w.getClientAngerLevel() : " warden=none"));
        return 1;
    }

    private static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(msg), false);
        }
    }
}
