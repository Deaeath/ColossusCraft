package adris.altoclef.platform;

import adris.altoclef.AltoClefPort;
import com.local.altoclef.AiChat;
import com.local.altoclef.AncientCityHelper;
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


@Mod(value = NeoForgeAltoClefMod.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeAltoClefMod {
    public static final String MOD_ID = "colossuscraft_core";
    private static final AltoClefPort PORT = new AltoClefPort(new NeoForgeAltoClefPlatform());

    public NeoForgeAltoClefMod(IEventBus modBus) {
        AncientCityHelper.init();
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
        // Only add commands that AltoClefQuestBot does NOT define.
        // AltoClefQuestBot owns the colossuscraft root and the /cc redirect.
        LiteralArgumentBuilder<CommandSourceStack> extra = Commands.literal("colossuscraft")
                // Upstream @-command wrappers
                .then(Commands.literal("gamer").executes(ctx -> coreExec("gamer")))
                .then(Commands.literal("marvion").executes(ctx -> coreExec("marvion")))
                .then(Commands.literal("hero").executes(ctx -> coreExec("hero")))
                .then(Commands.literal("idle").executes(ctx -> coreExec("idle")))
                .then(Commands.literal("coords").executes(ctx -> coreExec("coords")))
                .then(Commands.literal("list").executes(ctx -> coreExec("list")))
                .then(Commands.literal("reload").executes(ctx -> coreExec("reload_settings")))
                .then(Commands.literal("coverwithblocks").executes(ctx -> coreExec("coverwithblocks")))
                .then(Commands.literal("coverwithsand").executes(ctx -> coreExec("coverwithsand")))
                .then(Commands.literal("punk")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(com.local.altoclef.AltoClefCompletions::suggestPlayers)
                                .executes(ctx -> coreExec("punk " + StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(com.local.altoclef.AltoClefCompletions::suggestPlayers)
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests(com.local.altoclef.AltoClefCompletions::suggestItems)
                                        .executes(ctx -> coreExec("give " + StringArgumentType.getString(ctx, "player") + " " + StringArgumentType.getString(ctx, "item")))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> coreExec("give " + StringArgumentType.getString(ctx, "player") + " " + StringArgumentType.getString(ctx, "item") + " " + IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(Commands.literal("meat")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> coreExec("meat " + IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("gamma")
                        .executes(ctx -> coreExec("gamma"))
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .executes(ctx -> coreExec("gamma " + IntegerArgumentType.getInteger(ctx, "value")))))
                .then(Commands.literal("inventory")
                        .executes(ctx -> coreExec("inventory"))
                        .then(Commands.argument("item", StringArgumentType.word())
                                .suggests(com.local.altoclef.AltoClefCompletions::suggestItems)
                                .executes(ctx -> coreExec("inventory " + StringArgumentType.getString(ctx, "item")))))
                .then(Commands.literal("locate")
                        .then(Commands.argument("structure", StringArgumentType.word())
                                .executes(ctx -> coreExec("locate_structure " + StringArgumentType.getString(ctx, "structure")))))
                .then(Commands.literal("custom")
                        .then(Commands.argument("task", StringArgumentType.greedyString())
                                .executes(ctx -> coreExec("custom " + StringArgumentType.getString(ctx, "task")))))
                .then(Commands.literal("test")
                        .executes(ctx -> coreExec("test"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> coreExec("test " + StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("equip")
                        .executes(ctx -> coreExec("equip"))
                        .then(Commands.argument("tier_or_item", StringArgumentType.word())
                                .executes(ctx -> coreExec("equip " + StringArgumentType.getString(ctx, "tier_or_item")))))
                .then(Commands.literal("deposit")
                        .executes(ctx -> coreExec("deposit"))
                        .then(Commands.argument("items", StringArgumentType.greedyString())
                                .executes(ctx -> coreExec("deposit " + StringArgumentType.getString(ctx, "items")))))
                .then(Commands.literal("ai")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> AiChat.query(StringArgumentType.getString(ctx, "message")))))
                .then(AncientCityHelper.sneakCommand())
                .then(AncientCityHelper.mineCommand());
        event.getDispatcher().register(extra);
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

    private static int coreExec(String command) {
        PORT.executeCommand(command);
        return 1;
    }

    private static int baritone(String command) {
        if (PORT.core().runBaritone(command)) {
            PORT.core().log("Nav: " + command);
            return 1;
        }
        PORT.core().log("Nav command failed");
        return 0;
    }
}
