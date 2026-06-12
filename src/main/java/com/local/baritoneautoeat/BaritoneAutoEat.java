package com.local.baritoneautoeat;

import adris.altoclef.platform.NeoForgeAltoClefMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Mod(value = BaritoneAutoEat.MOD_ID, dist = Dist.CLIENT)
public final class BaritoneAutoEat {
    public static final String MOD_ID = "colossuscraft_autoeat";
    private static final int CONFIG_VERSION = 2;

    private static boolean loaded;
    private static boolean enabled = true;
    private static boolean useInventory = true;
    private static boolean preferOffhand = false;
    private static boolean restoreSlot = true;
    private static boolean safetyCheck = true;
    private static int threshold = 10;
    private static int cooldownTicks;
    private static boolean eating;
    private static boolean useHeld;
    private static int originalSlot = -1;
    private static int swappedInventorySlot = -1;
    private static InteractionHand eatingHand = InteractionHand.MAIN_HAND;
    private static int eatTicks;
    private static int expectedEatTicks;
    private static int startFoodLevel;
    private static float startSaturationLevel;
    private static int startStackCount;
    private static int notUsingTicks;

    public BaritoneAutoEat(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(BaritoneAutoEat::clientTick);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("autoeat")
                .then(Commands.literal("on").executes(ctx -> setEnabled(true)))
                .then(Commands.literal("off").executes(ctx -> setEnabled(false)))
                .then(Commands.literal("toggle").executes(ctx -> setEnabled(!enabled)))
                .then(Commands.literal("threshold").then(Commands.argument("food", IntegerArgumentType.integer(1, 19)).executes(ctx -> {
                    ensureLoaded();
                    threshold = IntegerArgumentType.getInteger(ctx, "food");
                    save();
                    say("ColossusCraft AutoEat threshold: " + threshold);
                    return 1;
                })))
                .then(Commands.literal("inventory")
                        .then(Commands.literal("on").executes(ctx -> setUseInventory(true)))
                        .then(Commands.literal("off").executes(ctx -> setUseInventory(false))))
                .then(Commands.literal("offhand")
                        .then(Commands.literal("on").executes(ctx -> setPreferOffhand(true)))
                        .then(Commands.literal("off").executes(ctx -> setPreferOffhand(false))))
                .then(Commands.literal("restore")
                        .then(Commands.literal("on").executes(ctx -> setRestoreSlot(true)))
                        .then(Commands.literal("off").executes(ctx -> setRestoreSlot(false))))
                .then(Commands.literal("safety")
                        .then(Commands.literal("on").executes(ctx -> setSafetyCheck(true)))
                        .then(Commands.literal("off").executes(ctx -> setSafetyCheck(false))))
                .then(Commands.literal("status").executes(ctx -> {
                    ensureLoaded();
                    say("ColossusCraft AutoEat: " + (enabled ? "ON" : "OFF") + " threshold=" + threshold + " inventory=" + useInventory + " offhand=" + preferOffhand + " restore=" + restoreSlot + " safety=" + safetyCheck);
                    return 1;
                }));
    }

    private static int setEnabled(boolean value) {
        ensureLoaded();
        enabled = value;
        if (!enabled) {
            stopEating(Minecraft.getInstance(), true, true);
        }
        save();
        say("ColossusCraft AutoEat: " + (enabled ? "ON" : "OFF"));
        return 1;
    }

    private static int setUseInventory(boolean value) {
        ensureLoaded();
        useInventory = value;
        save();
        say("ColossusCraft AutoEat inventory: " + (useInventory ? "ON" : "OFF"));
        return 1;
    }

    private static int setPreferOffhand(boolean value) {
        ensureLoaded();
        preferOffhand = value;
        save();
        say("ColossusCraft AutoEat offhand: " + (preferOffhand ? "ON" : "OFF"));
        return 1;
    }

    private static int setRestoreSlot(boolean value) {
        ensureLoaded();
        restoreSlot = value;
        save();
        say("ColossusCraft AutoEat restore: " + (restoreSlot ? "ON" : "OFF"));
        return 1;
    }

    private static int setSafetyCheck(boolean value) {
        ensureLoaded();
        safetyCheck = value;
        save();
        say("ColossusCraft AutoEat safety: " + (safetyCheck ? "ON" : "OFF"));
        return 1;
    }

    private static void clientTick(ClientTickEvent.Post event) {
        ensureLoaded();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!enabled || player == null || mc.level == null || mc.gameMode == null || player.isSpectator() || mc.screen != null) {
            stopEating(mc, true, true);
            return;
        }
        if (NeoForgeAltoClefMod.port().running()) {
            stopEating(mc, true, true);
            return;
        }

        if (eating) {
            continueEating(mc, player);
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        if (!player.canEat(false)) {
            return;
        }

        int foodSlot = findBestFoodSlot(player);
        if (foodSlot < 0) {
            cooldownTicks = 40;
            return;
        }

        if (!shouldEatNow(player, player.getInventory().getItem(foodSlot))) {
            return;
        }

        if (shouldWaitToEat(mc, player, foodUseTicks(player.getInventory().getItem(foodSlot), player))) {
            return;
        }

        if (player.isUsingItem() || mc.options.keyUse.isDown() || mc.options.keyAttack.isDown()) {
            return;
        }

        startEating(mc, player);
    }

    private static void startEating(Minecraft mc, LocalPlayer player) {
        if (preferOffhand && startOffhandEating(mc, player)) {
            return;
        }

        int selected = player.getInventory().selected;
        int foodSlot = findBestFoodSlot(player);
        if (foodSlot < 0) {
            cooldownTicks = 40;
            return;
        }

        originalSlot = selected;
        swappedInventorySlot = -1;
        if (foodSlot < 9) {
            selectHotbarSlot(mc, player, foodSlot);
        } else if (useInventory && canClickInventory(mc, player)) {
            mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, inventorySlotToMenuSlot(foodSlot), selected, ClickType.SWAP, player);
            swappedInventorySlot = foodSlot;
        } else {
            cooldownTicks = 20;
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!isFood(stack)) {
            stopEating(mc, true, true);
            cooldownTicks = 10;
            return;
        }

        eating = true;
        eatingHand = InteractionHand.MAIN_HAND;
        eatTicks = 0;
        expectedEatTicks = foodUseTicks(stack, player);
        startFoodLevel = player.getFoodData().getFoodLevel();
        startSaturationLevel = player.getFoodData().getSaturationLevel();
        startStackCount = stack.getCount();
        notUsingTicks = 0;
        mc.options.keyUse.setDown(true);
        useHeld = true;
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
    }

    private static boolean startOffhandEating(Minecraft mc, LocalPlayer player) {
        if (isFood(player.getOffhandItem())) {
            beginEating(mc, player, InteractionHand.OFF_HAND);
            return true;
        }
        if (!player.getOffhandItem().isEmpty() || !useInventory || !canClickInventory(mc, player)) {
            return false;
        }

        int foodSlot = findBestFoodSlot(player);
        if (foodSlot < 0) {
            cooldownTicks = 40;
            return true;
        }
        if (foodSlot < 9) {
            mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, 45, foodSlot, ClickType.SWAP, player);
        } else {
            mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, inventorySlotToMenuSlot(foodSlot), 0, ClickType.PICKUP, player);
            mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, 45, 0, ClickType.PICKUP, player);
        }
        if (!isFood(player.getOffhandItem())) {
            cooldownTicks = 10;
            return true;
        }
        beginEating(mc, player, InteractionHand.OFF_HAND);
        return true;
    }

    private static void beginEating(Minecraft mc, LocalPlayer player, InteractionHand hand) {
        eating = true;
        eatingHand = hand;
        originalSlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : -1;
        swappedInventorySlot = -1;
        eatTicks = 0;
        ItemStack stack = player.getItemInHand(hand);
        expectedEatTicks = foodUseTicks(stack, player);
        startFoodLevel = player.getFoodData().getFoodLevel();
        startSaturationLevel = player.getFoodData().getSaturationLevel();
        startStackCount = stack.getCount();
        notUsingTicks = 0;
        mc.options.keyUse.setDown(true);
        useHeld = true;
        mc.gameMode.useItem(player, hand);
    }

    private static void continueEating(Minecraft mc, LocalPlayer player) {
        eatTicks++;
        mc.options.keyAttack.setDown(false);
        if (!useHeld) {
            mc.options.keyUse.setDown(true);
            useHeld = true;
        }

        ItemStack held = player.getItemInHand(eatingHand);
        boolean consumed = player.getFoodData().getFoodLevel() > startFoodLevel
                || player.getFoodData().getSaturationLevel() > startSaturationLevel + 0.01F
                || (startStackCount > 0 && held.getCount() < startStackCount);
        if (consumed) {
            stopEating(mc, true, false);
            cooldownTicks = 10;
            return;
        }

        if (!isFood(held)) {
            stopEating(mc, true, true);
            cooldownTicks = 20;
            return;
        }

        boolean stillEating = player.isUsingItem() && isFood(player.getUseItem());
        if (stillEating) {
            notUsingTicks = 0;
        } else {
            notUsingTicks++;
            if (notUsingTicks >= 8 && mc.gameMode != null) {
                mc.gameMode.useItem(player, eatingHand);
                notUsingTicks = 0;
            }
        }

        if (eatTicks > expectedEatTicks + 40) {
            stopEating(mc, true, true);
            cooldownTicks = 40;
        }
    }

    private static void stopEating(Minecraft mc, boolean restore, boolean cancelUse) {
        if (mc != null && mc.options != null && useHeld) {
            mc.options.keyUse.setDown(false);
        }
        useHeld = false;

        LocalPlayer player = mc == null ? null : mc.player;
        if (cancelUse && player != null && player.isUsingItem() && isFood(player.getUseItem())) {
            player.stopUsingItem();
        }

        if (restore && restoreSlot && player != null && mc.gameMode != null && originalSlot >= 0) {
            if (eatingHand == InteractionHand.OFF_HAND) {
                // Offhand food is intentionally left in place so Baritone keeps the pick/tool selected.
            } else if (swappedInventorySlot >= 9 && canClickInventory(mc, player)) {
                mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, inventorySlotToMenuSlot(swappedInventorySlot), originalSlot, ClickType.SWAP, player);
            } else if (player.getInventory().selected != originalSlot) {
                selectHotbarSlot(mc, player, originalSlot);
            }
        }

        eating = false;
        eatTicks = 0;
        expectedEatTicks = 0;
        startFoodLevel = 0;
        startSaturationLevel = 0.0F;
        startStackCount = 0;
        notUsingTicks = 0;
        originalSlot = -1;
        swappedInventorySlot = -1;
        eatingHand = InteractionHand.MAIN_HAND;
    }

    public static boolean isEatingFood() {
        return eating;
    }

    public static boolean shouldPrioritizeEating() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return eating && player != null && shouldEatNow(player, findBestFoodStack(player));
    }

    public static void abortForCombat() {
        stopEating(Minecraft.getInstance(), true, true);
        cooldownTicks = 20;
    }

    private static int findBestFoodSlot(LocalPlayer player) {
        int limit = useInventory ? 36 : 9;
        int bestSlot = -1;
        double bestScore = -1.0D;
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            double score = foodScore(stack, player);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private static boolean isFood(ItemStack stack) {
        return isFoodCandidate(stack);
    }

    private static int foodUseTicks(ItemStack stack, LocalPlayer player) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            return food.eatDurationTicks();
        }
        return Math.max(32, stack.getUseDuration(player));
    }

    private static ItemStack findBestFoodStack(LocalPlayer player) {
        int slot = findBestFoodSlot(player);
        return slot < 0 ? ItemStack.EMPTY : player.getInventory().getItem(slot);
    }

    private static boolean shouldEatNow(LocalPlayer player, ItemStack bestFood) {
        if (!isFoodCandidate(bestFood)) {
            return false;
        }
        int foodLevel = player.getFoodData().getFoodLevel();
        float health = player.getHealth();
        if (foodLevel >= 20) {
            return false;
        }
        if (health <= 10.0F && foodLevel <= 19) {
            return true;
        }
        if (player.isOnFire() || player.hasEffect(MobEffects.WITHER) || health < 6.0F) {
            return true;
        }
        FoodProperties food = bestFood.get(DataComponents.FOOD);
        if (foodLevel < 15 && food != null && food.nutrition() == 20 - foodLevel) {
            return true;
        }
        if (foodLevel > threshold) {
            return health < 14.0F;
        }
        if (foodLevel <= threshold) {
            return true;
        }
        return false;
    }

    private static boolean isFoodCandidate(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return !id.equals("minecraft:golden_apple")
                && !id.equals("minecraft:enchanted_golden_apple")
                && stack.getItem() != Items.SPIDER_EYE
                && stack.get(DataComponents.FOOD) != null;
    }

    private static double foodScore(ItemStack stack, LocalPlayer player) {
        if (!isFoodCandidate(stack)) {
            return -1.0D;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) {
            return -1.0D;
        }
        float hunger = player.getFoodData().getFoodLevel();
        float saturation = player.getFoodData().getSaturationLevel();
        float hungerIfEaten = Math.min(hunger + food.nutrition(), 20.0F);
        float saturationIfEaten = Math.min(hungerIfEaten, saturation + food.saturation());
        float gainedSaturation = saturationIfEaten - saturation;
        float gainedHunger = hungerIfEaten - hunger;
        float hungerNotFilled = 20.0F - hungerIfEaten;
        float saturationWasted = food.saturation() - gainedSaturation;
        float hungerWasted = food.nutrition() - gainedHunger;
        boolean prioritizeSaturation = player.getHealth() < 8.0F;
        double score = (prioritizeSaturation ? gainedSaturation * 8.0D : gainedSaturation)
                - (prioritizeSaturation ? 0.0D : saturationWasted)
                - hungerWasted * 2.0D
                - hungerNotFilled;
        if (stack.getItem() == Items.ROTTEN_FLESH) {
            score -= 100.0D;
        }
        return score;
    }

    private static boolean shouldWaitToEat(Minecraft mc, LocalPlayer player, int eatTicks) {
        if (isActivelyMining(mc)) {
            return true;
        }
        if (!safetyCheck || mc.level == null) {
            return false;
        }

        AABB box = player.getBoundingBox().inflate(30.0D, 8.0D, 30.0D);
        for (Entity entity : mc.level.getEntities(player, box, BaritoneAutoEat::isThreatEntity)) {
            if (isDangerProjectile(entity) && projectileCanHitDuringEat(player, entity, eatTicks)) {
                return true;
            }
            if (entity instanceof LivingEntity living && entity instanceof Enemy && mobCanReachDuringEat(player, living, eatTicks)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isActivelyMining(Minecraft mc) {
        return mc.gameMode != null && (mc.gameMode.isDestroying() || mc.options.keyAttack.isDown());
    }

    private static boolean isThreatEntity(Entity entity) {
        if (entity instanceof Player) {
            return false;
        }
        if (entity instanceof Projectile) {
            return isDangerProjectile(entity);
        }
        if (entity instanceof LivingEntity living && entity instanceof Enemy) {
            return living.isAlive() && !living.isRemoved();
        }
        return false;
    }

    private static boolean isDangerProjectile(Entity entity) {
        if (!(entity instanceof Projectile projectile)) {
            return false;
        }
        if (!(entity instanceof AbstractArrow) && !(entity instanceof Fireball) && !(entity instanceof ShulkerBullet)) {
            return false;
        }
        Entity owner = projectile.getOwner();
        return entity.isAlive() && !entity.isRemoved() && !(owner instanceof Player);
    }

    private static boolean projectileCanHitDuringEat(LocalPlayer player, Entity projectile, int eatTicks) {
        Vec3 velocity = projectile.getDeltaMovement();
        double speedSqr = velocity.lengthSqr();
        if (speedSqr < 0.01D) {
            return false;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 toPlayer = eye.subtract(projectile.position());
        if (velocity.normalize().dot(toPlayer.normalize()) < 0.50D) {
            return false;
        }

        double ticksToClosest = toPlayer.dot(velocity) / speedSqr;
        if (ticksToClosest < 0.0D || ticksToClosest > eatTicks + 10.0D) {
            return false;
        }

        Vec3 closest = projectile.position().add(velocity.scale(ticksToClosest));
        return closest.distanceTo(eye) < 2.75D;
    }

    private static boolean mobCanReachDuringEat(LocalPlayer player, LivingEntity mob, int eatTicks) {
        double distance = Math.sqrt(player.distanceToSqr(mob));
        boolean lineOfSight = player.hasLineOfSight(mob);
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();

        if (isRangedMob(id)) {
            if (lineOfSight && distance <= rangedDangerRange(id)) {
                return true;
            }
            return distance <= 7.0D;
        }

        double speed = Math.max(horizontalSpeed(mob), mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        double reachWindow = 3.25D + speed * (eatTicks + 10.0D) * 1.25D;
        if (!lineOfSight) {
            reachWindow = Math.min(reachWindow, 6.0D);
        }
        return distance <= reachWindow;
    }

    private static boolean isRangedMob(String id) {
        return id.contains("skeleton")
                || id.equals("stray")
                || id.equals("pillager")
                || id.equals("blaze")
                || id.equals("ghast")
                || id.equals("witch")
                || id.equals("shulker")
                || id.equals("drowned")
                || id.equals("bogged");
    }

    private static double rangedDangerRange(String id) {
        if (id.equals("ghast")) {
            return 48.0D;
        }
        if (id.equals("blaze") || id.equals("shulker")) {
            return 28.0D;
        }
        if (id.equals("witch")) {
            return 12.0D;
        }
        return 22.0D;
    }

    private static double horizontalSpeed(Entity entity) {
        Vec3 movement = entity.getDeltaMovement();
        return Math.sqrt(movement.x * movement.x + movement.z * movement.z);
    }

    private static void selectHotbarSlot(Minecraft mc, LocalPlayer player, int slot) {
        if (slot < 0 || slot > 8 || player.getInventory().selected == slot) {
            return;
        }
        player.getInventory().selected = slot;
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    private static boolean canClickInventory(Minecraft mc, LocalPlayer player) {
        return mc.gameMode != null && mc.screen == null && player.containerMenu == player.inventoryMenu;
    }

    private static int inventorySlotToMenuSlot(int inventorySlot) {
        return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = configFile();
        boolean migrated = false;
        if (!Files.exists(file) && Files.exists(legacyConfigFile())) {
            file = legacyConfigFile();
            migrated = true;
        }
        if (!Files.exists(file)) {
            save();
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
            enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
            useInventory = Boolean.parseBoolean(props.getProperty("useInventory", "true"));
            preferOffhand = Boolean.parseBoolean(props.getProperty("preferOffhand", "false"));
            restoreSlot = Boolean.parseBoolean(props.getProperty("restoreSlot", "true"));
            safetyCheck = Boolean.parseBoolean(props.getProperty("safetyCheck", "true"));
            int configVersion = Integer.parseInt(props.getProperty("logicVersion", "1"));
            threshold = clamp(Integer.parseInt(props.getProperty("threshold", "10")), 1, 19);
            if (configVersion < CONFIG_VERSION && threshold > 10) {
                threshold = 10;
                save();
            } else if (migrated) {
                save();
            }
        } catch (Exception ignored) {
            enabled = true;
            useInventory = true;
            preferOffhand = false;
            restoreSlot = true;
            safetyCheck = true;
            threshold = 10;
            save();
        }
    }

    private static void save() {
        Path file = configFile();
        Properties props = new Properties();
        props.setProperty("enabled", Boolean.toString(enabled));
        props.setProperty("useInventory", Boolean.toString(useInventory));
        props.setProperty("preferOffhand", Boolean.toString(preferOffhand));
        props.setProperty("restoreSlot", Boolean.toString(restoreSlot));
        props.setProperty("safetyCheck", Boolean.toString(safetyCheck));
        props.setProperty("threshold", Integer.toString(threshold));
        props.setProperty("logicVersion", Integer.toString(CONFIG_VERSION));
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "ColossusCraft AutoEat client config");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path configFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("colossuscraft-autoeat.properties");
    }

    private static Path legacyConfigFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("baritone-autoeat.properties");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void say(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
