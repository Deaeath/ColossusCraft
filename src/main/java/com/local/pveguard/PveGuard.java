package com.local.pveguard;

import com.local.baritoneautoeat.BaritoneAutoEat;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;

@Mod(value = PveGuard.MOD_ID, dist = Dist.CLIENT)
public final class PveGuard {
    public static final String MOD_ID = "pveguard";

    private static final KeyMapping TOGGLE = new KeyMapping(
            "key.pveguard.toggle",
            InputConstants.KEY_K,
            "key.categories.pveguard"
    );

    private static boolean loaded;
    private static boolean enabled;
    private static boolean autoWeapon = true;
    private static boolean autoInventoryWeapon = true;
    private static boolean dodgeProjectiles = true;
    private static boolean autoShield = true;
    private static double range = 4.25D;
    private static int waitTicks;
    private static int dodgeTicks;
    private static int dodgeDir;
    private static boolean guardLeftHeld;
    private static boolean guardRightHeld;
    private static boolean guardJumpHeld;
    private static boolean guardSprintHeld;
    private static boolean guardUseHeld;

    public PveGuard(IEventBus modBus) {
        modBus.addListener(PveGuard::registerKeys);
        NeoForge.EVENT_BUS.addListener(PveGuard::registerCommands);
        NeoForge.EVENT_BUS.addListener(PveGuard::clientPreTick);
        NeoForge.EVENT_BUS.addListener(PveGuard::clientPostTick);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pveguard")
                .then(Commands.literal("on").executes(ctx -> setEnabled(true)))
                .then(Commands.literal("off").executes(ctx -> setEnabled(false)))
                .then(Commands.literal("toggle").executes(ctx -> setEnabled(!enabled)))
                .then(Commands.literal("range").then(Commands.argument("blocks", DoubleArgumentType.doubleArg(2.0D, 6.0D)).executes(ctx -> {
                    ensureLoaded();
                    range = DoubleArgumentType.getDouble(ctx, "blocks");
                    save();
                    say("PvE Guard range: " + range);
                    return 1;
                })))
                .then(Commands.literal("weapon")
                        .then(Commands.literal("on").executes(ctx -> setAutoWeapon(true)))
                        .then(Commands.literal("off").executes(ctx -> setAutoWeapon(false))))
                .then(Commands.literal("inventoryweapon")
                        .then(Commands.literal("on").executes(ctx -> setAutoInventoryWeapon(true)))
                        .then(Commands.literal("off").executes(ctx -> setAutoInventoryWeapon(false))))
                .then(Commands.literal("dodge")
                        .then(Commands.literal("on").executes(ctx -> setDodgeProjectiles(true)))
                        .then(Commands.literal("off").executes(ctx -> setDodgeProjectiles(false))))
                .then(Commands.literal("shield")
                        .then(Commands.literal("on").executes(ctx -> setAutoShield(true)))
                        .then(Commands.literal("off").executes(ctx -> setAutoShield(false))))
                .then(Commands.literal("status").executes(ctx -> {
                    ensureLoaded();
                    say("PvE Guard: " + (enabled ? "ON" : "OFF") + " range=" + range + " weapon=" + autoWeapon + " invWeapon=" + autoInventoryWeapon + " dodge=" + dodgeProjectiles + " shield=" + autoShield);
                    return 1;
                }))
        );
    }

    private static int setEnabled(boolean value) {
        ensureLoaded();
        enabled = value;
        if (!enabled) {
            releaseDodgeKeys(Minecraft.getInstance());
            releaseShield(Minecraft.getInstance());
        }
        save();
        say("PvE Guard: " + (enabled ? "ON" : "OFF"));
        return 1;
    }

    private static int setAutoWeapon(boolean value) {
        ensureLoaded();
        autoWeapon = value;
        save();
        say("PvE Guard weapon: " + (autoWeapon ? "ON" : "OFF"));
        return 1;
    }

    private static int setAutoInventoryWeapon(boolean value) {
        ensureLoaded();
        autoInventoryWeapon = value;
        save();
        say("PvE Guard inventory weapon: " + (autoInventoryWeapon ? "ON" : "OFF"));
        return 1;
    }

    private static int setDodgeProjectiles(boolean value) {
        ensureLoaded();
        dodgeProjectiles = value;
        if (!dodgeProjectiles) {
            releaseDodgeKeys(Minecraft.getInstance());
        }
        save();
        say("PvE Guard dodge: " + (dodgeProjectiles ? "ON" : "OFF"));
        return 1;
    }

    private static int setAutoShield(boolean value) {
        ensureLoaded();
        autoShield = value;
        if (!autoShield) {
            releaseShield(Minecraft.getInstance());
        }
        save();
        say("PvE Guard shield: " + (autoShield ? "ON" : "OFF"));
        return 1;
    }

    private static void clientPreTick(ClientTickEvent.Pre event) {
        ensureLoaded();
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || mc.level == null || mc.screen != null) {
            releaseDodgeKeys(mc);
            releaseShield(mc);
            return;
        }
        int incomingDir = 0;
        if (dodgeProjectiles) {
            incomingDir = findIncomingProjectileDodgeDir(mc, mc.player);
            if (incomingDir != 0) {
                dodgeDir = incomingDir;
                dodgeTicks = 6;
            }
        }

        if (dodgeProjectiles && dodgeTicks > 0 && dodgeDir != 0) {
            applyDodgeKeys(mc, dodgeDir);
            dodgeTicks--;
        } else {
            releaseDodgeKeys(mc);
        }

        if (autoShield && (incomingDir != 0 || shouldShieldMelee(mc, mc.player))) {
            raiseShield(mc, mc.player);
        } else {
            releaseShield(mc);
        }
    }

    private static void clientPostTick(ClientTickEvent.Post event) {
        ensureLoaded();

        while (TOGGLE.consumeClick()) {
            enabled = !enabled;
            save();
            say("PvE Guard: " + (enabled ? "ON" : "OFF"));
        }

        if (!enabled || waitTicks-- > 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null || mc.gameMode == null || player.isSpectator() || mc.screen != null) {
            return;
        }
        if (player.getAttackStrengthScale(0.0F) < 0.90F) {
            return;
        }

        AABB box = player.getBoundingBox().inflate(range, 2.0D, range);
        Entity target = mc.level.getEntities(player, box, PveGuard::isValidTarget).stream()
                .filter(player::hasLineOfSight)
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);

        if (target == null) {
            return;
        }

        BaritoneAutoEat.abortForCombat();
        releaseShield(mc);
        if (autoWeapon) {
            selectBestWeapon(mc, player);
        }

        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
        waitTicks = 2;
    }

    private static boolean isValidTarget(Entity entity) {
        if (entity instanceof Player) {
            return false;
        }
        if (!(entity instanceof Enemy) || !(entity instanceof LivingEntity living)) {
            return false;
        }
        if (!living.isAlive() || living.isRemoved()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        if (living instanceof ZombifiedPiglin piglin && !isHostileToPlayer(piglin, mc.player)) {
            return false;
        }
        return living.distanceToSqr(mc.player) <= range * range;
    }

    private static boolean isHostileToPlayer(ZombifiedPiglin piglin, LocalPlayer player) {
        return piglin.getTarget() == player || player.getUUID().equals(piglin.getPersistentAngerTarget());
    }

    private static void selectBestWeapon(Minecraft mc, LocalPlayer player) {
        int selected = player.getInventory().selected;
        int bestSlot = selected;
        double bestScore = weaponScore(player.getInventory().getItem(selected));
        int limit = autoInventoryWeapon ? 36 : 9;
        for (int slot = 0; slot < limit; slot++) {
            double score = weaponScore(player.getInventory().getItem(slot));
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        if (bestScore <= 1.0D || bestSlot == selected) {
            return;
        }
        if (bestSlot < 9) {
            player.getInventory().selected = bestSlot;
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundSetCarriedItemPacket(bestSlot));
            }
            return;
        }
        if (autoInventoryWeapon && canClickInventory(mc, player)) {
            mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, inventorySlotToMenuSlot(bestSlot), selected, ClickType.SWAP, player);
        }
    }

    private static double weaponScore(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        double[] score = {1.0D};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (!attribute.is(Attributes.ATTACK_DAMAGE)) {
                return;
            }
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                score[0] += modifier.amount();
            } else if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                score[0] += modifier.amount();
            } else if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                score[0] *= 1.0D + modifier.amount();
            }
        });
        return score[0];
    }

    private static boolean shouldShieldMelee(Minecraft mc, LocalPlayer player) {
        if (player.getAttackStrengthScale(0.0F) >= 0.90F) {
            return false;
        }
        AABB box = player.getBoundingBox().inflate(3.25D, 1.5D, 3.25D);
        return mc.level.getEntities(player, box, PveGuard::isValidTarget).stream()
                .anyMatch(player::hasLineOfSight);
    }

    private static void raiseShield(Minecraft mc, LocalPlayer player) {
        BaritoneAutoEat.abortForCombat();
        InteractionHand hand = shieldHand(player);
        if (hand == null) {
            equipShieldToOffhand(mc, player);
            hand = shieldHand(player);
        }
        if (hand == null || mc.gameMode == null) {
            return;
        }
        if (!mc.options.keyUse.isDown()) {
            guardUseHeld = true;
        }
        mc.options.keyUse.setDown(true);
        if (!player.isUsingItem() || !isShield(player.getUseItem())) {
            mc.gameMode.useItem(player, hand);
            player.startUsingItem(hand);
        }
    }

    private static void releaseShield(Minecraft mc) {
        boolean owned = guardUseHeld;
        if (mc == null || mc.options == null) {
            guardUseHeld = false;
            return;
        }
        if (guardUseHeld) {
            mc.options.keyUse.setDown(false);
            guardUseHeld = false;
        }
        if (owned && mc.player != null && mc.player.isUsingItem() && isShield(mc.player.getUseItem())) {
            mc.player.stopUsingItem();
        }
    }

    private static InteractionHand shieldHand(LocalPlayer player) {
        if (isShield(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        if (isShield(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    private static boolean isShield(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ShieldItem;
    }

    private static boolean isUsingFood(LocalPlayer player) {
        return player.isUsingItem() && player.getUseItem().get(DataComponents.FOOD) != null;
    }

    private static void equipShieldToOffhand(Minecraft mc, LocalPlayer player) {
        if (!player.getOffhandItem().isEmpty() || !canClickInventory(mc, player)) {
            return;
        }
        int shieldSlot = findShieldSlot(player);
        if (shieldSlot < 0) {
            return;
        }
        if (shieldSlot < 9) {
            mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, 45, shieldSlot, ClickType.SWAP, player);
        } else {
            mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, inventorySlotToMenuSlot(shieldSlot), 0, ClickType.PICKUP, player);
            mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, 45, 0, ClickType.PICKUP, player);
        }
    }

    private static int findShieldSlot(LocalPlayer player) {
        for (int slot = 0; slot < 36; slot++) {
            if (isShield(player.getInventory().getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean canClickInventory(Minecraft mc, LocalPlayer player) {
        return mc.gameMode != null && mc.screen == null && player.containerMenu == player.inventoryMenu;
    }

    private static int inventorySlotToMenuSlot(int inventorySlot) {
        return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
    }

    private static int findIncomingProjectileDodgeDir(Minecraft mc, LocalPlayer player) {
        AABB box = player.getBoundingBox().inflate(7.0D, 4.0D, 7.0D);
        Vec3 eye = player.getEyePosition();
        return mc.level.getEntities(player, box, PveGuard::isDangerProjectile).stream()
                .map(projectile -> dodgeDirFor(player, projectile, eye))
                .filter(dir -> dir != 0)
                .findFirst()
                .orElse(0);
    }

    private static boolean isDangerProjectile(Entity entity) {
        if (!(entity instanceof Projectile projectile)) {
            return false;
        }
        if (!(entity instanceof AbstractArrow) && !(entity instanceof Fireball) && !(entity instanceof ShulkerBullet)) {
            return false;
        }
        if (!entity.isAlive() || entity.isRemoved() || entity.getDeltaMovement().lengthSqr() < 0.01D) {
            return false;
        }
        Entity owner = projectile.getOwner();
        return !(owner instanceof Player);
    }

    private static int dodgeDirFor(LocalPlayer player, Entity projectile, Vec3 eye) {
        Vec3 velocity = projectile.getDeltaMovement();
        double speedSqr = velocity.lengthSqr();
        if (speedSqr < 0.01D) {
            return 0;
        }

        Vec3 fromProjectile = eye.subtract(projectile.position());
        if (velocity.normalize().dot(fromProjectile.normalize()) < 0.55D) {
            return 0;
        }

        double ticksToClosest = fromProjectile.dot(velocity) / speedSqr;
        if (ticksToClosest < 0.0D || ticksToClosest > 14.0D) {
            return 0;
        }

        Vec3 closest = projectile.position().add(velocity.scale(ticksToClosest));
        if (closest.distanceTo(eye) > 2.15D) {
            return 0;
        }

        Vec3 path = new Vec3(velocity.x, 0.0D, velocity.z);
        if (path.lengthSqr() < 0.001D) {
            return 0;
        }
        Vec3 lateral = new Vec3(-path.z, 0.0D, path.x).normalize();
        Vec3 awayFromPath = new Vec3(player.getX() - closest.x, 0.0D, player.getZ() - closest.z);
        if (lateral.dot(awayFromPath) < 0.0D) {
            lateral = lateral.reverse();
        }

        Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 playerRight = new Vec3(-look.z, 0.0D, look.x).normalize();
        return lateral.dot(playerRight) >= 0.0D ? 1 : -1;
    }

    private static void applyDodgeKeys(Minecraft mc, int dir) {
        if (mc == null || mc.options == null) {
            return;
        }
        if (!guardSprintHeld && !mc.options.keySprint.isDown()) {
            mc.options.keySprint.setDown(true);
            guardSprintHeld = true;
        }
        if (!guardJumpHeld && !mc.options.keyJump.isDown()) {
            mc.options.keyJump.setDown(true);
            guardJumpHeld = true;
        }
        if (dir < 0) {
            if (guardRightHeld) {
                mc.options.keyRight.setDown(false);
                guardRightHeld = false;
            }
            if (!guardLeftHeld && !mc.options.keyLeft.isDown()) {
                mc.options.keyLeft.setDown(true);
                guardLeftHeld = true;
            }
        } else if (dir > 0) {
            if (guardLeftHeld) {
                mc.options.keyLeft.setDown(false);
                guardLeftHeld = false;
            }
            if (!guardRightHeld && !mc.options.keyRight.isDown()) {
                mc.options.keyRight.setDown(true);
                guardRightHeld = true;
            }
        }
    }

    private static void releaseDodgeKeys(Minecraft mc) {
        dodgeTicks = 0;
        dodgeDir = 0;
        if (mc == null || mc.options == null) {
            guardLeftHeld = false;
            guardRightHeld = false;
            guardJumpHeld = false;
            guardSprintHeld = false;
            return;
        }
        if (guardSprintHeld) {
            mc.options.keySprint.setDown(false);
            guardSprintHeld = false;
        }
        if (guardJumpHeld) {
            mc.options.keyJump.setDown(false);
            guardJumpHeld = false;
        }
        if (guardLeftHeld) {
            mc.options.keyLeft.setDown(false);
            guardLeftHeld = false;
        }
        if (guardRightHeld) {
            mc.options.keyRight.setDown(false);
            guardRightHeld = false;
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;

        Path file = configFile();
        if (!Files.exists(file)) {
            save();
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
            enabled = Boolean.parseBoolean(props.getProperty("enabled", "false"));
            autoWeapon = Boolean.parseBoolean(props.getProperty("autoWeapon", "true"));
            autoInventoryWeapon = Boolean.parseBoolean(props.getProperty("autoInventoryWeapon", "true"));
            dodgeProjectiles = Boolean.parseBoolean(props.getProperty("dodgeProjectiles", "true"));
            autoShield = Boolean.parseBoolean(props.getProperty("autoShield", "true"));
            range = clamp(Double.parseDouble(props.getProperty("range", "4.25")), 2.0D, 6.0D);
        } catch (Exception ignored) {
            enabled = false;
            autoWeapon = true;
            autoInventoryWeapon = true;
            dodgeProjectiles = true;
            autoShield = true;
            range = 4.25D;
            save();
        }
    }

    private static void save() {
        Path file = configFile();
        Properties props = new Properties();
        props.setProperty("enabled", Boolean.toString(enabled));
        props.setProperty("autoWeapon", Boolean.toString(autoWeapon));
        props.setProperty("autoInventoryWeapon", Boolean.toString(autoInventoryWeapon));
        props.setProperty("dodgeProjectiles", Boolean.toString(dodgeProjectiles));
        props.setProperty("autoShield", Boolean.toString(autoShield));
        props.setProperty("range", Double.toString(range));
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "PvE Guard client config");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path configFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("pveguard.properties");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void say(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }
}




