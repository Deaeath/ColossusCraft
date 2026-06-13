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
                .then(Commands.argument("block", StringArgumentType.word())
                        .executes(ctx -> startMine(StringArgumentType.getString(ctx, "block"), 1))
                        .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                .executes(ctx -> startMine(
                                        StringArgumentType.getString(ctx, "block"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count")))));
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        boolean inDeepDark = mc.level.getBiome(player.blockPosition()).is(Biomes.DEEP_DARK);
        boolean shouldSneak = manualSneak || inDeepDark;

        // Warden awareness — check before applying sneak so we can freeze or release
        Warden angryWarden = nearbyAngryWarden(mc, player);
        if (angryWarden != null && angryWarden.getClientAngerLevel() >= FREEZE_ANGER
                && (angryWarden.getTarget() == null || !angryWarden.getTarget().equals(player))) {
            // Warden is building anger but hasn't locked on yet — freeze
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
            say("Warden calmed — resuming");
        }

        applySneak(shouldSneak);
    }

    /** Force Baritone's input override to hold or release sneak. */
    private static void applySneak(boolean on) {
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone()
                    .getInputOverrideHandler()
                    .setInputForceState(Input.SNEAK, on);
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

    private static int startMine(String blockName, int count) {
        // Accept bare name ("allthemodium_ore") or namespaced ("allthemodium:allthemodium_ore")
        String full = blockName.contains(":") ? blockName : "minecraft:" + blockName;

        // Try registry lookup first
        Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(full)).orElse(null);
        if (block == null && !blockName.contains(":")) {
            // Caller probably meant a mod block — try without minecraft: prefix via mineByName
            try {
                BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mineByName(count, blockName);
                say("Mining: " + blockName + (count > 1 ? " x" + count : ""));
                return 1;
            } catch (Throwable e) {
                say("Unknown block: " + blockName);
                return 0;
            }
        }
        if (block == null) {
            say("Unknown block: " + blockName);
            return 0;
        }
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(count, block);
            say("Mining: " + blockName + (count > 1 ? " x" + count : ""));
        } catch (Throwable e) {
            say("Mine failed: " + e.getMessage());
            return 0;
        }
        return 1;
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
