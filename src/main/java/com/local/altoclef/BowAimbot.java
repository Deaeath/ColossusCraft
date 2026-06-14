package com.local.altoclef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Tick-driven bow/crossbow aimbot.
 *
 * Activate: BowAimbot.setEnabled(true)
 * Each tick:
 *   1. Find nearest living enemy within MAX_RANGE
 *   2. Equip bow/crossbow if not held
 *   3. Hold USE key to charge
 *   4. When charge >= MIN_CHARGE, aim with ballistic pitch + lead prediction, release
 *
 * Arrow ballistics (vanilla 1.21):
 *   full-charge velocity  = 3.0 blocks/tick
 *   gravity               = 0.05 blocks/tick²
 *   drag                  = 0.99 per tick (ignored for this range — negligible)
 */
public final class BowAimbot {

    private static final double MAX_RANGE = 50.0;
    private static final double GRAVITY = 0.05;           // blocks / tick²
    private static final int    MIN_CHARGE_TICKS = 20;    // 20 ticks = full charge for vanilla bow
    private static final int    CROSSBOW_CHARGE_TICKS = 25;

    private static boolean enabled = false;
    private static boolean initialized = false;
    private static boolean charging = false;
    private static int forcedTargetId = -1;

    private BowAimbot() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener(BowAimbot::onTick);
    }

    public static void setEnabled(boolean on) {
        if (enabled == on) {
            if (!on) releaseUse();
            return;
        }
        enabled = on;
        if (!on) {
            forcedTargetId = -1;
            releaseUse();
        }
        say("Bow aimbot: " + (on ? "ON" : "OFF"));
    }

    public static boolean isEnabled() { return enabled; }

    public static void setForcedTarget(LivingEntity target) {
        forcedTargetId = target == null ? -1 : target.getId();
    }

    public static void clearForcedTarget() {
        forcedTargetId = -1;
    }

    // ── Tick ────────────────────────────────────────────────────────────────

    private static void onTick(ClientTickEvent.Post event) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;

        // Ensure bow/crossbow is in hand
        if (!hasBowInHand(player)) {
            if (!equipBow(player)) {
                // No bow available — disable silently
                return;
            }
        }

        LivingEntity target = findTarget(mc, player);
        if (target == null) {
            releaseUse();
            charging = false;
            return;
        }

        // Start / continue charging
        mc.options.keyUse.setDown(true);
        charging = true;

        ItemStack held = player.getMainHandItem();
        boolean isCrossbow = held.getItem() instanceof CrossbowItem;

        int useTicks = isCrossbow
            ? player.getTicksUsingItem()
            : player.getTicksUsingItem();

        int requiredTicks = isCrossbow ? CROSSBOW_CHARGE_TICKS : MIN_CHARGE_TICKS;
        if (useTicks < requiredTicks) return; // still charging

        // Charged — aim and fire
        Vec3 aimPoint = predictPosition(target, player);
        if (aimPoint == null) return;

        double dx = aimPoint.x - player.getX();
        // Aim at eye position (same height hasClearShot uses) — avoids blocks at warden torso level
        double dy = aimPoint.y + target.getEyeHeight() - (player.getY() + player.getEyeHeight());
        double dz = aimPoint.z - player.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        double velocity = isCrossbow ? 3.15 : bowVelocity(useTicks);
        Double pitch = ballisticPitch(horizDist, dy, velocity);
        if (pitch == null) return; // target out of range for current charge

        double yaw = Math.toDegrees(Math.atan2(-dx, dz));

        float pitchF = pitch.floatValue();
        player.setYRot((float) yaw);
        player.setXRot(pitchF);
        player.yRotO = (float) yaw;
        player.xRotO = pitchF;

        // Release
        mc.options.keyUse.setDown(false);
        charging = false;
    }

    // ── Ballistics ──────────────────────────────────────────────────────────

    /**
     * Given horizontal distance D, vertical offset H (target - shooter eye),
     * and initial arrow velocity V (blocks/tick), returns the launch pitch in degrees
     * (negative = upward in MC convention).
     *
     * Accounts for arrow drag (0.99/tick): the effective velocity over the flight
     * is approximated as V × drag^(flight_time / 2), reducing the range. Without
     * this correction shots fall short at distances > 15 blocks.
     */
    private static Double ballisticPitch(double D, double H, double V) {
        // Estimate flight ticks at this velocity, then compute average drag factor
        double flightEst = D / Math.max(V, 0.5);
        double dragFactor = Math.pow(0.99, flightEst / 2.0); // midpoint approximation
        double Ve = V * dragFactor;                            // drag-corrected effective velocity

        double k = GRAVITY * D * D / (2.0 * Ve * Ve);
        double discriminant = D * D - 4.0 * k * (H + k);
        if (discriminant < 0) return null;
        double tanA = (D - Math.sqrt(discriminant)) / (2.0 * k);
        double angleRad = Math.atan(tanA);
        return -Math.toDegrees(angleRad);
    }

    /** Velocity (blocks/tick) at given charge ticks for a regular bow (vanilla formula). */
    private static double bowVelocity(int ticks) {
        float f = Math.min(ticks / 20.0f, 1.0f);
        f = (f * f + f * 2.0f) / 3.0f;  // vanilla BowItem.getPowerForTime
        return f * 3.0;
    }

    // ── Target prediction ───────────────────────────────────────────────────

    /**
     * Predict where target will be when the arrow arrives.
     * Uses a single-step lead: estimate flight time assuming current distance,
     * then offset by target velocity × flight_time.
     */
    private static Vec3 predictPosition(LivingEntity target, LocalPlayer player) {
        Vec3 tPos = target.position();
        double dist = tPos.distanceTo(player.position());
        double velocity = bowVelocity(MIN_CHARGE_TICKS); // conservative
        double flightTicks = dist / velocity;            // rough estimate

        Vec3 tVel = target.getDeltaMovement();
        return tPos.add(tVel.scale(flightTicks));
    }

    // ── Targeting ───────────────────────────────────────────────────────────

    private static LivingEntity findTarget(Minecraft mc, LocalPlayer player) {
        if (forcedTargetId >= 0) {
            Entity forced = mc.level.getEntity(forcedTargetId);
            if (forced instanceof LivingEntity le
                    && le.isAlive()
                    && le.distanceToSqr(player) <= MAX_RANGE * MAX_RANGE
                    && hasSafeShot(mc, player, le)) {
                return le;
            }
            return null;
        }
        LivingEntity best = null;
        double bestDist = MAX_RANGE * MAX_RANGE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (!le.isAlive() || le == player) continue;
            if (!(le instanceof Enemy)) continue;
            if (!hasSafeShot(mc, player, le)) continue;
            double d = le.distanceToSqr(player);
            if (d < bestDist) {
                best = le;
                bestDist = d;
            }
        }
        return best;
    }

    public static boolean hasSafeShot(Minecraft mc, LocalPlayer player, LivingEntity target) {
        return hasClearShot(mc, player, target) && hasArrowPathClear(mc, player, target);
    }

    public static boolean hasClearShot(Minecraft mc, LocalPlayer player, LivingEntity target) {
        if (mc.level == null || player == null || target == null) return false;
        Vec3 start = player.getEyePosition();
        Vec3 end = target.getEyePosition();
        // OUTLINE mode: uses block selection shape, not collision shape.
        // This correctly ignores partial/transparent decorative blocks (candles, lanterns,
        // sculk features) that have tiny colliders but aren't actual line-of-sight blockers.
        HitResult hit = mc.level.clip(new ClipContext(start, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return true;
        // Give a 2-block tolerance so the hit can be right at the target's position
        return hit.getLocation().distanceToSqr(start) >= end.distanceToSqr(start) - 4.0D;
    }

    public static boolean hasArrowPathClear(Minecraft mc, LocalPlayer player, LivingEntity target) {
        if (mc.level == null || player == null || target == null) return false;
        Vec3 start = player.getEyePosition();
        Vec3 end = target.getEyePosition();
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 0.001D) return false;
        Double pitchDeg = ballisticPitch(horiz, dy, 3.0D);
        if (pitchDeg == null) return false;
        double yaw = Math.atan2(-dx, dz);
        double pitch = Math.toRadians(pitchDeg);
        Vec3 velocity = new Vec3(
                -Math.sin(yaw) * Math.cos(pitch) * 3.0D,
                -Math.sin(pitch) * 3.0D,
                Math.cos(yaw) * Math.cos(pitch) * 3.0D);
        Vec3 prev = start;
        Vec3 pos = start;
        for (int tick = 0; tick < 80; tick++) {
            pos = pos.add(velocity);
            HitResult hit = mc.level.clip(new ClipContext(prev, pos,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS
                    && hit.getLocation().distanceToSqr(start) < end.distanceToSqr(start) - 1.0D) {
                return false;
            }
            if (pos.distanceToSqr(end) < 4.0D || pos.distanceToSqr(start) > end.distanceToSqr(start) + 16.0D) {
                return true;
            }
            prev = pos;
            velocity = velocity.scale(0.99D).add(0.0D, -GRAVITY, 0.0D);
        }
        return false;
    }

    // ── Equipment ───────────────────────────────────────────────────────────

    private static boolean hasBowInHand(LocalPlayer player) {
        ItemStack held = player.getMainHandItem();
        return held.getItem() instanceof BowItem || held.getItem() instanceof CrossbowItem;
    }

    private static boolean equipBow(LocalPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof BowItem || s.getItem() instanceof CrossbowItem) {
                inv.selected = i;
                return true;
            }
        }
        return false;
    }

    private static void releaseUse() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) mc.options.keyUse.setDown(false);
    }

    private static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal(msg), false);
    }
}
