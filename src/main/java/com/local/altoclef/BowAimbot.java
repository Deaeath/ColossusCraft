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
    private static final int    MIN_CHARGE_TICKS = 18;    // ≥ 18 ticks → near-full power
    private static final int    CROSSBOW_CHARGE_TICKS = 25;

    private static boolean enabled = false;
    private static boolean initialized = false;
    private static boolean charging = false;

    private BowAimbot() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener(BowAimbot::onTick);
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        if (!on) releaseUse();
        say("Bow aimbot: " + (on ? "ON" : "OFF"));
    }

    public static boolean isEnabled() { return enabled; }

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
        double dy = aimPoint.y + target.getEyeHeight() * 0.5 - (player.getY() + player.getEyeHeight());
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
     * and arrow velocity V (blocks/tick), returns the launch pitch in degrees
     * (negative = upward in MC convention).
     *
     * Physics: y = V*sin(a)*t - 0.5*g*t²
     *          x = V*cos(a)*t   →  t = D / (V*cos(a))
     * Substituting and solving the quadratic in tan(a):
     *   g*D²/(2V²) * tan²(a) - D*tan(a) + (H + g*D²/(2V²)) = 0
     */
    private static Double ballisticPitch(double D, double H, double V) {
        double k = GRAVITY * D * D / (2.0 * V * V);
        // quadratic: k*t² - D*t + (H + k) = 0  where t = tan(a)
        double discriminant = D * D - 4.0 * k * (H + k);
        if (discriminant < 0) return null; // out of range
        // Use the lower-angle solution (flatter trajectory)
        double tanA = (D - Math.sqrt(discriminant)) / (2.0 * k);
        double angleRad = Math.atan(tanA);
        return -Math.toDegrees(angleRad); // MC pitch: negative = look up
    }

    /** Velocity (blocks/tick) at given charge ticks for a regular bow. */
    private static double bowVelocity(int ticks) {
        double charge = Math.min(ticks / 20.0, 1.0);
        return charge * 3.0;
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
        LivingEntity best = null;
        double bestDist = MAX_RANGE * MAX_RANGE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (!le.isAlive() || le == player) continue;
            if (!(le instanceof Enemy)) continue;
            double d = le.distanceToSqr(player);
            if (d < bestDist) {
                best = le;
                bestDist = d;
            }
        }
        return best;
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
