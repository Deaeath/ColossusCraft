package com.local.pveguard;

import adris.altoclef.AltoClefPort;
import adris.altoclef.platform.NeoForgeAltoClefMod;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskChain;
import adris.altoclef.tasks.movement.RunAwayFromEntitiesTask;
import com.local.altoclef.EmergencyHome;
import com.local.altoclef.InventoryView;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.network.chat.Component;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Mod(value = PveGuard.MOD_ID, dist = Dist.CLIENT)
public final class PveGuard {
    public static final String MOD_ID = "colossuscraft_guard";

    private static final KeyMapping TOGGLE = new KeyMapping(
            "key.colossuscraft.guard.toggle",
            InputConstants.KEY_K,
            "key.categories.colossuscraft"
    );
    private static final AltoClefPort CORE = NeoForgeAltoClefMod.port();

    private static boolean loaded;
    private static boolean enabled = true;
    private static boolean combat = true;
    private static boolean eating = true;
    private static boolean wardenDodge = true;  // /home+/back on warden attacks
    private static boolean wardenFlee = true;   // run-away from targeting wardens
    private static boolean startedCore;
    private static double range = 6.0D;
    private static int tick;
    private static int wardenFleeRemainingTicks = 0; // ticks until /back fires after shriek /home
    private static int shriekIgnoreTicks = 0;         // grace window after /back so stale animations don't re-trigger
    private static boolean fleeingWarden = false;     // currently running flee user-task
    private static Task savedUserTask = null;         // task to restore after flee finishes
    private static List<Entity> fleeTargets = new ArrayList<>(); // live warden refs for flee task

    /** Read by WardenFleeChain to decide whether to flee nearby wardens. */
    public static boolean isWardenFleeEnabled() { return wardenFlee; }

    public PveGuard(IEventBus modBus) {
        EmergencyHome.init();
        InventoryView.init(modBus);
        modBus.addListener(PveGuard::registerKeys);
        NeoForge.EVENT_BUS.addListener(PveGuard::registerCommands);
        NeoForge.EVENT_BUS.addListener(PveGuard::clientTick);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(guardCommand("pveguard"));
        event.getDispatcher().register(guardCommand("colossusguard"));
        event.getDispatcher().register(guardCommand("ccguard"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> guardCommand(String name) {
        return Commands.literal(name)
                .then(Commands.literal("on").executes(ctx -> setEnabled(true)))
                .then(Commands.literal("off").executes(ctx -> setEnabled(false)))
                .then(Commands.literal("toggle").executes(ctx -> setEnabled(!enabled)))
                .then(Commands.literal("combat")
                        .then(Commands.literal("on").executes(ctx -> setCombat(true)))
                        .then(Commands.literal("off").executes(ctx -> setCombat(false))))
                .then(Commands.literal("eat")
                        .then(Commands.literal("on").executes(ctx -> setEating(true)))
                        .then(Commands.literal("off").executes(ctx -> setEating(false))))
                .then(Commands.literal("eating")
                        .then(Commands.literal("on").executes(ctx -> setEating(true)))
                        .then(Commands.literal("off").executes(ctx -> setEating(false))))
                .then(Commands.literal("range").then(Commands.argument("blocks", DoubleArgumentType.doubleArg(2.0D, 24.0D)).executes(ctx -> {
                    ensureLoaded();
                    range = DoubleArgumentType.getDouble(ctx, "blocks");
                    applyCoreSettings();
                    save();
                    say("ColossusCraft Guard range: " + range);
                    return 1;
                })))
                .then(Commands.literal("weapon").then(Commands.literal("on").executes(ctx -> legacy("weapon"))).then(Commands.literal("off").executes(ctx -> legacy("weapon"))))
                .then(Commands.literal("inventoryweapon").then(Commands.literal("on").executes(ctx -> legacy("inventory weapon"))).then(Commands.literal("off").executes(ctx -> legacy("inventory weapon"))))
                .then(Commands.literal("dodge").then(Commands.literal("on").executes(ctx -> setCombat(true))).then(Commands.literal("off").executes(ctx -> setCombat(false))))
                .then(Commands.literal("shield").then(Commands.literal("on").executes(ctx -> setCombat(true))).then(Commands.literal("off").executes(ctx -> setCombat(false))))
                .then(Commands.literal("warden")
                        .then(Commands.literal("dodge")
                                .then(Commands.literal("on").executes(ctx -> setWardenDodge(true)))
                                .then(Commands.literal("off").executes(ctx -> setWardenDodge(false)))
                                .then(Commands.literal("toggle").executes(ctx -> setWardenDodge(!wardenDodge))))
                        .then(Commands.literal("flee")
                                .then(Commands.literal("on").executes(ctx -> setWardenFlee(true)))
                                .then(Commands.literal("off").executes(ctx -> setWardenFlee(false)))
                                .then(Commands.literal("toggle").executes(ctx -> setWardenFlee(!wardenFlee))))
                        .then(Commands.literal("on").executes(ctx -> { setWardenDodge(true); setWardenFlee(true); return 1; }))
                        .then(Commands.literal("off").executes(ctx -> { setWardenDodge(false); setWardenFlee(false); return 1; })))
                .then(Commands.literal("status").executes(ctx -> status()));
    }

    private static void clientTick(ClientTickEvent.Post event) {
        ensureLoaded();
        while (TOGGLE.consumeClick()) {
            setEnabled(!enabled);
        }
        if (++tick % 10 == 0) {
            applyCoreSettings();
        }
        if (enabled && (combat || eating)) {
            ensureCoreRunning();
        } else if (startedCore && !hasUserTask()) {
            CORE.stop();
            startedCore = false;
        }
        tickShriekDodge();
        tickWardenFlee();
    }

    private static void tickShriekDodge() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!wardenDodge) return;

        if (shriekIgnoreTicks > 0) shriekIgnoreTicks--;

        // /back countdown — runs unconditionally (we're at home, wardens not nearby)
        if (wardenFleeRemainingTicks > 0) {
            wardenFleeRemainingTicks--;
            if (wardenFleeRemainingTicks == 0) {
                if (mc.getConnection() != null) {
                    mc.getConnection().sendUnsignedCommand("back");
                    say("Returning via /back.");
                }
                shriekIgnoreTicks = 200;
            }
        }

        if (shriekIgnoreTicks > 0) return;

        // Detect ANY warden attack: sonic boom windup OR melee swing
        boolean wardenAttacking = false;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Warden w) || !w.isAlive()) continue;
            float dist = w.distanceTo(mc.player);
            boolean sonic = w.sonicBoomAnimationState.isStarted() && dist <= 15;
            boolean melee = w.attackAnimationState.isStarted() && dist <= 6;
            if (sonic || melee) {
                wardenAttacking = true;
                break;
            }
        }

        if (wardenAttacking && wardenFleeRemainingTicks == 0) {
            wardenFleeRemainingTicks = 300; // 15s then /back
            EmergencyHome.goHome("warden attack");
            say("Warden attack! /home — /back in 15s");
        }
    }

    private static void tickWardenFlee() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!wardenFlee || !CORE.running()) return;
        if (com.local.altoclef.WardenTrapTask.isActive) return;

        // Collect live warden references that are targeting us or too close
        List<Entity> threats = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Warden w) || !w.isAlive()) continue;
            float dist = w.distanceTo(mc.player);
            if ((w.getTarget() == mc.player && dist < 24) || dist < 10) threats.add(w);
        }

        if (!threats.isEmpty()) {
            if (!fleeingWarden) {
                fleeingWarden = true;
                savedUserTask = CORE.core().getUserTaskChain().getCurrentTask();
                fleeTargets = threats; // live entity refs — positions update automatically
                CORE.core().runUserTask(new RunAwayFromEntitiesTask(() -> fleeTargets, 20, 0.8) {}, () -> {
                    fleeingWarden = false;
                    Task toRestore = savedUserTask;
                    savedUserTask = null;
                    fleeTargets = new ArrayList<>();
                    if (toRestore != null) {
                        say("Warden gone, resuming task.");
                        CORE.core().runUserTask(toRestore, null);
                    } else {
                        say("Warden gone.");
                    }
                });
                say("Warden targeting! Fleeing...");
            } else {
                // update live targets so flee task tracks any new wardens
                fleeTargets = threats;
            }
        } else if (fleeingWarden) {
            // Warden list cleared — flee task will finish naturally via distance check
        }
    }

    private static int setEnabled(boolean value) {
        ensureLoaded();
        enabled = value;
        if (enabled) {
            ensureCoreRunning();
        } else if (startedCore && !hasUserTask()) {
            CORE.stop();
            startedCore = false;
        }
        save();
        say("ColossusCraft Guard: " + (enabled ? "ON" : "OFF") + " (ColossusCraft core)");
        return 1;
    }

    private static int setCombat(boolean value) {
        ensureLoaded();
        combat = value;
        applyCoreSettings();
        if (enabled && combat) {
            ensureCoreRunning();
        }
        save();
        say("ColossusCraft Guard combat: " + (combat ? "ON" : "OFF"));
        return 1;
    }

    private static int setEating(boolean value) {
        ensureLoaded();
        eating = value;
        applyCoreSettings();
        if (enabled && eating) {
            ensureCoreRunning();
        }
        save();
        say("ColossusCraft Guard eating: " + (eating ? "ON" : "OFF"));
        return 1;
    }

    private static int setWardenDodge(boolean value) {
        ensureLoaded();
        wardenDodge = value;
        if (!value) { wardenFleeRemainingTicks = 0; shriekIgnoreTicks = 0; }
        save();
        say("Warden dodge (/home+/back): " + (value ? "ON" : "OFF"));
        return 1;
    }

    private static int setWardenFlee(boolean value) {
        ensureLoaded();
        wardenFlee = value;
        save();
        say("Warden flee (run-away): " + (value ? "ON" : "OFF"));
        return 1;
    }

    private static int legacy(String name) {
        say("ColossusCraft Guard " + name + ": ColossusCraft handles this inside combat");
        return 1;
    }

    private static int status() {
        ensureLoaded();
        applyCoreSettings();
        Task userTask = CORE.core().getUserTaskChain().getCurrentTask();
        TaskChain chain = CORE.core().getTaskRunner().getCurrentTaskChain();
        say("ColossusCraft Guard: " + (enabled ? "ON" : "OFF")
                + " combat=" + combat
                + " eating=" + eating
                + " wardenDodge=" + wardenDodge
                + " wardenFlee=" + wardenFlee
                + " core=" + CORE.running()
                + " range=" + range
                + " task=" + (userTask == null ? "none" : userTask)
                + " chain=" + (chain == null ? "none" : chain));
        return 1;
    }

    private static void ensureCoreRunning() {
        applyCoreSettings();
        boolean wasRunning = CORE.running();
        CORE.start();
        if (!wasRunning) {
            startedCore = true;
        }
    }

    private static void applyCoreSettings() {
        CORE.core().getModSettings().setMobDefense(combat);
        CORE.core().getModSettings().setAutoEat(eating);
        CORE.core().getModSettings().setDodgeProjectiles(combat);
        CORE.core().getModSettings().setDealWithAnnoyingHostiles(combat);
        CORE.core().getMobDefenseChain().setForceFieldRange(range);
    }

    private static boolean hasUserTask() {
        return CORE.core().getUserTaskChain().getCurrentTask() != null;
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
            combat = Boolean.parseBoolean(props.getProperty("combat", props.getProperty("autoWeapon", "true")));
            eating = Boolean.parseBoolean(props.getProperty("eating", "true"));
            wardenDodge = Boolean.parseBoolean(props.getProperty("wardenDodge", "true"));
            wardenFlee = Boolean.parseBoolean(props.getProperty("wardenFlee", "true"));
            range = clamp(Double.parseDouble(props.getProperty("range", "6.0")), 2.0D, 24.0D);
            if (migrated) {
                save();
            }
        } catch (Exception ignored) {
            enabled = true;
            combat = true;
            eating = true;
            range = 6.0D;
            save();
        }
        applyCoreSettings();
    }

    private static void save() {
        Path file = configFile();
        Properties props = new Properties();
        props.setProperty("enabled", Boolean.toString(enabled));
        props.setProperty("combat", Boolean.toString(combat));
        props.setProperty("eating", Boolean.toString(eating));
        props.setProperty("wardenDodge", Boolean.toString(wardenDodge));
        props.setProperty("wardenFlee", Boolean.toString(wardenFlee));
        props.setProperty("range", Double.toString(range));
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "ColossusCraft Guard config");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path configFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("colossuscraft-guard.properties");
    }

    private static Path legacyConfigFile() {
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
