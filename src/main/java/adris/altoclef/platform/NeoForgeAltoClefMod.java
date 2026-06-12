package adris.altoclef.platform;

import adris.altoclef.AltoClefPort;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.BlockBreakingCancelEvent;
import adris.altoclef.eventbus.events.BlockBreakingEvent;
import adris.altoclef.eventbus.events.BlockInteractEvent;
import adris.altoclef.eventbus.events.ScreenOpenEvent;
import adris.altoclef.trackers.LocateResultTracker;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.fml.common.Mod;

import java.util.Comparator;
import java.util.stream.Collectors;

@Mod(value = NeoForgeAltoClefMod.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeAltoClefMod {
    public static final String MOD_ID = "colossuscraft_core";
    private static final AltoClefPort PORT = new AltoClefPort(new NeoForgeAltoClefPlatform());

    public NeoForgeAltoClefMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(NeoForgeAltoClefMod::registerCommands);
        NeoForge.EVENT_BUS.addListener(NeoForgeAltoClefMod::clientTick);
        NeoForge.EVENT_BUS.addListener(NeoForgeAltoClefMod::clientChat);
        NeoForge.EVENT_BUS.addListener(NeoForgeAltoClefMod::clientChatReceived);
        NeoForge.EVENT_BUS.addListener(NeoForgeAltoClefMod::screenOpening);
        NeoForge.EVENT_BUS.addListener(NeoForgeAltoClefMod::rightClickBlock);
        NeoForge.EVENT_BUS.addListener(NeoForgeAltoClefMod::leftClickBlock);
    }

    public static AltoClefPort port() {
        return PORT;
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(coreCommand("altoclef"));
        event.getDispatcher().register(coreCommand("colossuscraft"));
        event.getDispatcher().register(coreCommand("cc"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> coreCommand(String name) {
        return Commands.literal(name)
                .then(Commands.literal("on").executes(ctx -> coreOn()))
                .then(Commands.literal("off").executes(ctx -> coreOff()))
                .then(Commands.literal("status").executes(ctx -> coreStatus()))
                .then(Commands.literal("help").executes(ctx -> help()))
                .then(Commands.literal("stop").executes(ctx -> coreExec("stop")))
                .then(Commands.literal("get")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .executes(ctx -> coreGet(StringArgumentType.getString(ctx, "item"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> coreGet(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("kill")
                        .then(Commands.argument("entity", StringArgumentType.word())
                                .executes(ctx -> coreExec("kill " + StringArgumentType.getString(ctx, "entity")))))
                .then(Commands.literal("exec")
                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                .executes(ctx -> coreExec(StringArgumentType.getString(ctx, "command")))))
                .then(Commands.literal("baritone")
                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                .executes(ctx -> baritone(StringArgumentType.getString(ctx, "command")))));
    }

    private static void clientTick(ClientTickEvent.Post event) {
        PORT.tick();
        // Index opened containers + drive cache persistence every tick, even when the bot is idle.
        adris.altoclef.trackers.storage.ContainerSubTracker containers = PORT.core().getContainerTracker();
        if (containers != null) {
            containers.tickScan();
        }
    }

    private static void clientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message == null) return;
        message = message.trim();
        if (PORT.core().getCommandExecutor().isClientCommand(message)) {
            event.setCanceled(true);
            PORT.executeCommand(message);
        }
    }

    private static void clientChatReceived(ClientChatReceivedEvent event) {
        LocateResultTracker.acceptChat(event.getMessage().getString());
    }

    private static void screenOpening(ScreenEvent.Opening event) {
        EventBus.publish(new ScreenOpenEvent(event.getNewScreen(), true));
    }

    private static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EventBus.publish(new BlockInteractEvent(event.getHitVec()));
    }

    private static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.ABORT
                || event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.STOP) {
            EventBus.publish(new BlockBreakingCancelEvent());
        } else {
            EventBus.publish(new BlockBreakingEvent(event.getPos(), 0));
        }
    }

    private static int coreOn() {
        PORT.start();
        return 1;
    }

    private static int coreOff() {
        PORT.stop();
        return 1;
    }

    private static int coreStatus() {
        adris.altoclef.tasksystem.Task task = PORT.core().getUserTaskChain().getCurrentTask();
        PORT.core().log("ColossusCraft core: " + (PORT.running() ? "ON" : "OFF") + " task=" + (task == null ? "none" : task));
        return 1;
    }

    private static int help() {
        String commands = PORT.core().getCommandExecutor().allCommands().stream()
                .sorted(Comparator.comparing(Command::getName))
                .map(command -> "@" + command.getHelpRepresentation())
                .collect(Collectors.joining(", "));
        PORT.core().log("ColossusCraft commands: " + commands);
        return 1;
    }

    private static int coreGet(String item, int count) {
        return PORT.runItemTask(item, count) ? 1 : 0;
    }

    private static int coreExec(String command) {
        PORT.executeCommand(command);
        return 1;
    }

    private static int baritone(String command) {
        if (PORT.core().runBaritone(command)) {
            PORT.core().log("Baritone: " + command);
            return 1;
        }
        PORT.core().log("Baritone command failed");
        return 0;
    }
}
