package com.local.pveguard;

import adris.altoclef.AltoClefPort;
import adris.altoclef.platform.NeoForgeAltoClefMod;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskChain;
import com.local.altoclef.EmergencyHome;
import com.local.altoclef.InventoryView;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
    private static boolean startedCore;
    private static double range = 6.0D;
    private static int tick;

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
