package com.local.altoclef;

import adris.altoclef.AltoClefPort;
import adris.altoclef.platform.NeoForgeAltoClefMod;
import baritone.api.BaritoneAPI;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class EmergencyHome {
    private static boolean initialized;
    private static boolean enabled = true;
    private static float thresholdHealth = 6.0F;
    private static int cooldownTicks;
    private static boolean trackingFall;
    private static double fallPeakY;
    private static float trackedFallDistance;

    private EmergencyHome() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        NeoForge.EVENT_BUS.addListener(EmergencyHome::clientTick);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("home")
                .then(Commands.literal("on").executes(ctx -> setEnabled(true)))
                .then(Commands.literal("off").executes(ctx -> setEnabled(false)))
                .then(Commands.literal("status").executes(ctx -> status()))
                .then(Commands.literal("test").executes(ctx -> goHome("manual test") ? 1 : 0))
                .then(Commands.literal("threshold")
                        .then(Commands.argument("hearts", DoubleArgumentType.doubleArg(1.0D, 10.0D))
                                .executes(ctx -> setThreshold(DoubleArgumentType.getDouble(ctx, "hearts")))));
    }

    private static void clientTick(ClientTickEvent.Post event) {
        tick();
    }

    static void tick() {
        if (!enabled) {
            return;
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.getConnection() == null || !player.isAlive()
                || player.isCreative() || player.isSpectator()) {
            return;
        }
        updateFallTracker(player);
        String reason = emergencyReason(mc, player);
        if (reason != null) {
            goHome(reason);
        }
    }

    static int setEnabled(boolean value) {
        enabled = value;
        say("Emergency /home: " + (enabled ? "ON" : "OFF") + " threshold=" + thresholdHearts() + " hearts");
        return 1;
    }

    private static int setThreshold(double hearts) {
        thresholdHealth = (float) (hearts * 2.0D);
        say("Emergency /home threshold: " + hearts + " hearts");
        return 1;
    }

    private static int status() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        String hp = player == null ? "n/a" : format(player.getHealth() / 2.0F) + " hearts raw, "
                + format((player.getHealth() + player.getAbsorptionAmount()) / 2.0F) + " effective";
        say("Emergency /home: " + (enabled ? "ON" : "OFF")
                + " threshold=" + thresholdHearts() + " hearts"
                + " cooldown=" + cooldownTicks
                + " hp=" + hp);
        return 1;
    }

    public static boolean goHome(String reason) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            say("Emergency /home failed: not connected");
            return false;
        }
        stopEverything();
        boolean sent = mc.getConnection().sendUnsignedCommand("home");
        if (!sent) {
            mc.getConnection().sendCommand("home");
        }
        cooldownTicks = 200;
        String message = "EMERGENCY /home: " + reason;
        say(message);
        System.out.println("[ColossusCraft] " + message);
        return true;
    }

    private static String emergencyReason(Minecraft mc, LocalPlayer player) {
        float rawHealth = player.getHealth();
        float effectiveHealth = rawHealth + player.getAbsorptionAmount();
        if (rawHealth <= thresholdHealth) {
            return "low health " + format(rawHealth / 2.0F) + " hearts";
        }
        if (effectiveHealth <= thresholdHealth) {
            return "low effective health " + format(effectiveHealth / 2.0F) + " hearts";
        }
        // Intentionally omitted: "hostile near" check. Triggering /home whenever a mob
        // is within range during active combat (e.g. shulkers with @hero) causes an
        // endless cancel-and-return loop. The health threshold above is sufficient.
        if (player.isInLava() && !player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return "lava";
        }
        if (player.hasEffect(MobEffects.WITHER) && rawHealth <= 12.0F) {
            return "wither";
        }
        if (player.isOnFire() && !player.hasEffect(MobEffects.FIRE_RESISTANCE) && rawHealth <= 12.0F) {
            return "burning";
        }
        if (player.isInWater() && player.getAirSupply() <= 40) {
            return "drowning";
        }
        if (fallIsLikelyLethal(mc, player, rawHealth)) {
            return "lethal fall";
        }
        return null;
    }

    private static boolean hostileNear(Minecraft mc, LocalPlayer player, double range) {
        double limit = range * range;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Enemy && entity.isAlive() && !entity.isRemoved()
                    && entity.distanceToSqr(player) <= limit) {
                return true;
            }
        }
        return false;
    }

    private static boolean safeFromFall(LocalPlayer player) {
        return player.onGround() || player.isInWater() || player.isSwimming() || player.onClimbable();
    }

    private static void updateFallTracker(LocalPlayer player) {
        if (safeFromFall(player)) {
            player.fallDistance = 0.0F;
            trackingFall = false;
            fallPeakY = player.getY();
            trackedFallDistance = 0.0F;
            return;
        }
        if (!trackingFall) {
            trackingFall = true;
            fallPeakY = player.getY();
            trackedFallDistance = 0.0F;
        }
        if (player.getDeltaMovement().y > 0.0D) {
            fallPeakY = Math.max(fallPeakY, player.getY());
        }
        trackedFallDistance = (float) Math.max(0.0D, fallPeakY - player.getY());
    }

    private static boolean fallIsLikelyLethal(Minecraft mc, LocalPlayer player, float health) {
        if (safeFromFall(player) || player.getDeltaMovement().y >= -0.5) {
            return false;
        }
        int minY = mc.level == null ? -64 : mc.level.getMinBuildHeight();
        if (player.getY() < minY + 4 && !hasLandingBelow(mc, player, 16.0D)) {
            adris.altoclef.Debug.logInternal("[EmergencyHome] void fall: trackedFallDistance="
                    + trackedFallDistance
                    + " clientFallDistance=" + player.fallDistance
                    + " yVel=" + player.getDeltaMovement().y
                    + " y=" + player.getY()
                    + " minY=" + minY);
            return true;
        }
        if (player.hasEffect(MobEffects.SLOW_FALLING) || hasWaterBucket(player)) {
            return false;
        }
        // Suppress only while the MLG task is actually running. Fall distance
        // is tracked locally below, so no post-MLG grace window is needed here.
        try {
            adris.altoclef.AltoClefPort port = adris.altoclef.platform.NeoForgeAltoClefMod.port();
            if (port != null && port.running()) {
                adris.altoclef.chains.MLGBucketFallChain mlg = port.core().getMLGBucketChain();
                if (mlg.pausesBaritone()) {
                    return false;
                }
            }
        } catch (Throwable ignored) {
        }
        boolean lethal = trackedFallDistance - 3.0F >= health - 2.0F;
        if (lethal) {
            adris.altoclef.Debug.logInternal("[EmergencyHome] lethal fall: trackedFallDistance="
                    + trackedFallDistance
                    + " clientFallDistance=" + player.fallDistance
                    + " fallPeakY=" + fallPeakY
                    + " yVel=" + player.getDeltaMovement().y
                    + " y=" + player.getY()
                    + " health=" + health);
        } else if (player.fallDistance > trackedFallDistance + 4.0F) {
            adris.altoclef.Debug.logInternal("[EmergencyHome] ignored stale client fallDistance="
                    + player.fallDistance
                    + " trackedFallDistance=" + trackedFallDistance
                    + " yVel=" + player.getDeltaMovement().y
                    + " y=" + player.getY());
        }
        return lethal;
    }

    private static boolean hasLandingBelow(Minecraft mc, LocalPlayer player, double distance) {
        if (mc.level == null) {
            return false;
        }
        net.minecraft.world.phys.Vec3 start = player.position();
        net.minecraft.world.phys.Vec3 end = start.add(0.0D, -distance, 0.0D);
        BlockHitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, player));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    private static boolean hasWaterBucket(LocalPlayer player) {
        return player.getInventory().contains(new net.minecraft.world.item.ItemStack(Items.WATER_BUCKET));
    }

    private static void stopEverything() {
        try {
            AltoClefPort port = NeoForgeAltoClefMod.port();
            if (port.core().getUserTaskChain().getCurrentTask() != null) {
                port.core().getUserTaskChain().cancel(port.core());
            }
            port.core().stopPathing();
        } catch (Throwable ignored) {
        }
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
        } catch (Throwable ignored) {
        }
    }

    private static String thresholdHearts() {
        return format(thresholdHealth / 2.0F);
    }

    private static String format(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05F) {
            return Integer.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static void say(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
