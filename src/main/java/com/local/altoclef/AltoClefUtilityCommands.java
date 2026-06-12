package com.local.altoclef;

import adris.altoclef.AltoClef;
import adris.altoclef.AltoClefPort;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.platform.NeoForgeAltoClefMod;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasks.construction.ConstructNetherPortalTask;
import adris.altoclef.tasks.container.StashInRangeTask;
import adris.altoclef.tasks.container.StoreInAnyContainerTask;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.misc.PlaceBedAndSetSpawnTask;
import adris.altoclef.tasks.misc.RavageDesertTemplesTask;
import adris.altoclef.tasks.misc.RavageRuinedPortalsTask;
import adris.altoclef.tasks.misc.SleepThroughNightTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GoToStrongholdPortalTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.tasks.resources.GetElytraTask;
import adris.altoclef.tasks.resources.TradeWithPiglinsTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Arrays;
import java.util.Locale;

final class AltoClefUtilityCommands {
    private static final AltoClefPort PORT = NeoForgeAltoClefMod.port();
    private static final DaemonState DAEMONS = new DaemonState();
    private static boolean initialized;
    private static int tick;

    private AltoClefUtilityCommands() {
    }

    static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        NeoForge.EVENT_BUS.addListener(AltoClefUtilityCommands::registerCommands);
        NeoForge.EVENT_BUS.addListener(AltoClefUtilityCommands::clientTick);
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("colossuscraft")
                .then(barterBranch())
                .then(sweepBranch("sweep"))
                .then(utilityBranch())
                .then(taskBranch()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> barterBranch() {
        return Commands.literal("barter")
                .then(Commands.literal("stop").executes(ctx -> barterDaemon(false)))
                .then(Commands.literal("status").executes(ctx -> utilityStatus()))
                .then(Commands.literal("daemon")
                        .then(Commands.literal("off").executes(ctx -> barterDaemon(false)))
                        .then(Commands.literal("on")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests(AltoClefCompletions::suggestItems)
                                        .executes(ctx -> barterDaemon(StringArgumentType.getString(ctx, "item"), 16, 32))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> barterDaemon(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"), 32))
                                                .then(Commands.argument("gold", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> barterDaemon(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"), IntegerArgumentType.getInteger(ctx, "gold"))))))
                .then(Commands.argument("item", StringArgumentType.word())
                        .suggests(AltoClefCompletions::suggestItems)
                        .executes(ctx -> barter(StringArgumentType.getString(ctx, "item"), 16, 32))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> barter(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"), 32))
                                .then(Commands.argument("gold", IntegerArgumentType.integer(1))
                                        .executes(ctx -> barter(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"), IntegerArgumentType.getInteger(ctx, "gold"))))));
    }

    private static void registerSweep(RegisterClientCommandsEvent event, String root) {
        event.getDispatcher().register(Commands.literal(root)
                .then(Commands.literal("off").executes(ctx -> sweepOff()))
                .then(Commands.literal("clear").executes(ctx -> sweepOff()))
                .then(Commands.literal("status").executes(ctx -> utilityStatus()))
                .then(Commands.literal("once")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .suggests(AltoClefCompletions::suggestItems)
                                .executes(ctx -> pickup(StringArgumentType.getString(ctx, "item"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> pickup(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("on")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .suggests(AltoClefCompletions::suggestItems)
                                .executes(ctx -> sweepAdd(StringArgumentType.getString(ctx, "item"), 1, true))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> sweepAdd(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"), true)))))
                .then(Commands.literal("add")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .suggests(AltoClefCompletions::suggestItems)
                                .executes(ctx -> sweepAdd(StringArgumentType.getString(ctx, "item"), 1, false))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> sweepAdd(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"), false))))));
    }

    private static void registerUtility(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("utility")
                .then(Commands.literal("status").executes(ctx -> utilityStatus()))
                .then(Commands.literal("stop").executes(ctx -> stopAll()))
                .then(Commands.literal("pause").executes(ctx -> pauseDaemons(true)))
                .then(Commands.literal("resume").executes(ctx -> pauseDaemons(false))));
    }

    private static void registerTaskCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("loot")
                .then(Commands.literal("ruined_portals").executes(ctx -> run(new RavageRuinedPortalsTask(), "Loot ruined portals")))
                .then(Commands.literal("desert_temples").executes(ctx -> run(new RavageDesertTemplesTask(), "Loot desert temples"))));
        event.getDispatcher().register(Commands.literal("elytra")
                .executes(ctx -> run(new GetElytraTask(), "Get elytra")));
        event.getDispatcher().register(Commands.literal("portal")
                .then(Commands.literal("build").executes(ctx -> run(new ConstructNetherPortalTask(), "Build Nether portal")))
                .then(Commands.literal("nether").executes(ctx -> run(new ConstructNetherPortalTask(), "Build Nether portal"))));
        event.getDispatcher().register(Commands.literal("dimension")
                .then(Commands.literal("nether").executes(ctx -> run(new DefaultGoToDimensionTask(Dimension.NETHER), "Go to Nether")))
                .then(Commands.literal("overworld").executes(ctx -> run(new DefaultGoToDimensionTask(Dimension.OVERWORLD), "Go to Overworld")))
                .then(Commands.literal("end").executes(ctx -> run(new DefaultGoToDimensionTask(Dimension.END), "Go to End")))
                .then(Commands.literal("stronghold").executes(ctx -> run(new GoToStrongholdPortalTask(12), "Go to stronghold portal"))));
        event.getDispatcher().register(Commands.literal("location")
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(AltoClefCompletions::suggestLocations)
                        .executes(ctx -> location(StringArgumentType.getString(ctx, "target")))));
        event.getDispatcher().register(Commands.literal("sleepnow")
                .executes(ctx -> run(new SleepThroughNightTask(), "Sleep through night")));
        event.getDispatcher().register(Commands.literal("setspawn")
                .executes(ctx -> run(new PlaceBedAndSetSpawnTask(), "Set spawn")));
        event.getDispatcher().register(Commands.literal("foodstock")
                .executes(ctx -> run(new CollectFoodTask(60), "Stock food"))
                .then(Commands.argument("units", IntegerArgumentType.integer(1))
                        .executes(ctx -> run(new CollectFoodTask(IntegerArgumentType.getInteger(ctx, "units")), "Stock food"))));
        event.getDispatcher().register(Commands.literal("gear")
                .then(Commands.literal("armor")
                        .then(Commands.argument("tier_or_item", StringArgumentType.word())
                                .suggests(AltoClefCompletions::suggestArmor)
                                .executes(ctx -> armor(StringArgumentType.getString(ctx, "tier_or_item"))))));
        event.getDispatcher().register(Commands.literal("equiparmor")
                .then(Commands.argument("tier_or_item", StringArgumentType.word())
                        .suggests(AltoClefCompletions::suggestArmor)
                        .executes(ctx -> armor(StringArgumentType.getString(ctx, "tier_or_item")))));
        event.getDispatcher().register(Commands.literal("depositopen")
                .executes(ctx -> run(new StoreInAnyContainerTask(false), "Deposit inventory in open container"))
                .then(Commands.argument("item", StringArgumentType.word())
                        .suggests(AltoClefCompletions::suggestItems)
                        .executes(ctx -> deposit(StringArgumentType.getString(ctx, "item"), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> deposit(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"))))));
        event.getDispatcher().register(Commands.literal("stash")
                .then(Commands.literal("open")
                        .executes(ctx -> run(new StoreInAnyContainerTask(false), "Deposit inventory in open container"))
                        .then(Commands.argument("item", StringArgumentType.word())
                                .suggests(AltoClefCompletions::suggestItems)
                                .executes(ctx -> deposit(StringArgumentType.getString(ctx, "item"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> deposit(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("range")
                        .then(Commands.argument("x0", IntegerArgumentType.integer())
                                .then(Commands.argument("y0", IntegerArgumentType.integer())
                                        .then(Commands.argument("z0", IntegerArgumentType.integer())
                                                .then(Commands.argument("x1", IntegerArgumentType.integer())
                                                        .then(Commands.argument("y1", IntegerArgumentType.integer())
                                                                .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                                        .executes(ctx -> stashRange(
                                                                                IntegerArgumentType.getInteger(ctx, "x0"),
                                                                                IntegerArgumentType.getInteger(ctx, "y0"),
                                                                                IntegerArgumentType.getInteger(ctx, "z0"),
                                                                                IntegerArgumentType.getInteger(ctx, "x1"),
                                                                                IntegerArgumentType.getInteger(ctx, "y1"),
                                                                                IntegerArgumentType.getInteger(ctx, "z1"),
                                                                                ItemTarget.EMPTY))
                                                                        .then(Commands.argument("item", StringArgumentType.word())
                                                                                .suggests(AltoClefCompletions::suggestItems)
                                                                                .executes(ctx -> stashRange(
                                                                                        IntegerArgumentType.getInteger(ctx, "x0"),
                                                                                        IntegerArgumentType.getInteger(ctx, "y0"),
                                                                                        IntegerArgumentType.getInteger(ctx, "z0"),
                                                                                        IntegerArgumentType.getInteger(ctx, "x1"),
                                                                                        IntegerArgumentType.getInteger(ctx, "y1"),
                                                                                        IntegerArgumentType.getInteger(ctx, "z1"),
                                                                                        target(StringArgumentType.getString(ctx, "item"), 1)))
                                                                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                                                        .executes(ctx -> stashRange(
                                                                                                IntegerArgumentType.getInteger(ctx, "x0"),
                                                                                                IntegerArgumentType.getInteger(ctx, "y0"),
                                                                                                IntegerArgumentType.getInteger(ctx, "z0"),
                                                                                                IntegerArgumentType.getInteger(ctx, "x1"),
                                                                                                IntegerArgumentType.getInteger(ctx, "y1"),
                                                                                                IntegerArgumentType.getInteger(ctx, "z1"),
                                                                                                target(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"))))))))))))));
    }

    private static void clientTick(ClientTickEvent.Post event) {
        if (!DAEMONS.anyActive() || DAEMONS.paused || ++tick % 10 != 0 || !playerReady()) {
            return;
        }
        ensureCore();
        if (hasUserTask()) {
            return;
        }
        AltoClef mod = PORT.core();
        if (DAEMONS.sweepTargets.length > 0 && mod.getEntityTracker().itemDropped(DAEMONS.sweepTargets)) {
            runDaemon(new PickupDroppedItemTask(DAEMONS.sweepTargets, true), "Sweep drops");
            return;
        }
        if (DAEMONS.barterTarget != null && !targetMet(DAEMONS.barterTarget)) {
            runDaemon(new TradeWithPiglinsTask(DAEMONS.barterGoldBuffer, DAEMONS.barterTarget), "Barter " + DAEMONS.barterTarget);
        }
    }

    private static int barter(String item, int count, int gold) {
        ItemTarget target = target(item, count);
        if (target.isEmpty()) {
            return unknown(item);
        }
        return run(new TradeWithPiglinsTask(gold, target), "Barter " + target + " gold=" + gold);
    }

    private static int barterDaemon(String item, int count, int gold) {
        ItemTarget target = target(item, count);
        if (target.isEmpty()) {
            return unknown(item);
        }
        DAEMONS.barterTarget = target;
        DAEMONS.barterGoldBuffer = gold;
        say("Barter daemon: ON " + target + " gold=" + gold);
        return 1;
    }

    private static int barterDaemon(boolean enabled) {
        if (!enabled) {
            DAEMONS.barterTarget = null;
            cancelIfCurrent(TradeWithPiglinsTask.class);
            say("Barter daemon: OFF");
        }
        return 1;
    }

    private static int sweepAdd(String item, int count, boolean replace) {
        ItemTarget target = target(item, count);
        if (target.isEmpty()) {
            return unknown(item);
        }
        DAEMONS.sweepTargets = replace ? new ItemTarget[]{target} : append(DAEMONS.sweepTargets, target);
        say("Sweep daemon: ON " + Arrays.toString(DAEMONS.sweepTargets));
        return 1;
    }

    private static int sweepOff() {
        DAEMONS.sweepTargets = new ItemTarget[0];
        cancelIfCurrent(PickupDroppedItemTask.class);
        say("Sweep daemon: OFF");
        return 1;
    }

    private static int pickup(String item, int count) {
        ItemTarget target = target(item, count);
        if (target.isEmpty()) {
            return unknown(item);
        }
        return run(new PickupDroppedItemTask(target, true), "Pick up " + target);
    }

    private static int armor(String spec) {
        ItemTarget[] targets = armorTargets(spec);
        if (targets.length == 0) {
            say("Unknown armor: " + spec);
            return 0;
        }
        return run(new EquipArmorTask(targets), "Equip armor " + spec);
    }

    private static int deposit(String item, int count) {
        ItemTarget target = target(item, count);
        if (target.isEmpty()) {
            return unknown(item);
        }
        return run(new StoreInAnyContainerTask(false, target), "Deposit " + target);
    }

    private static int stashRange(int x0, int y0, int z0, int x1, int y1, int z1, ItemTarget target) {
        ItemTarget[] targets = target == null || target.isEmpty() ? new ItemTarget[0] : new ItemTarget[]{target};
        return run(new StashInRangeTask(new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1), targets), "Stash in range");
    }

    private static int location(String target) {
        String resolved = AltoClefCompletions.resolveLocation(target);
        String key = resolved == null ? AltoClefCompletions.normalizeItemName(target) : AltoClefCompletions.normalizeItemName(resolved);
        return switch (key) {
            case "overworld", "minecraft:overworld" -> run(new DefaultGoToDimensionTask(Dimension.OVERWORLD), "Go to Overworld");
            case "nether", "the_nether", "minecraft:the_nether" -> run(new DefaultGoToDimensionTask(Dimension.NETHER), "Go to Nether");
            case "end", "the_end", "minecraft:the_end" -> run(new DefaultGoToDimensionTask(Dimension.END), "Go to End");
            case "stronghold" -> run(new GoToStrongholdPortalTask(12), "Go to stronghold portal");
            case "ruined_portals" -> run(new RavageRuinedPortalsTask(), "Loot ruined portals");
            case "desert_temples" -> run(new RavageDesertTemplesTask(), "Loot desert temples");
            case "elytra" -> run(new GetElytraTask(), "Get elytra");
            default -> {
                say("Location route not ported yet: " + target);
                yield 0;
            }
        };
    }

    private static int utilityStatus() {
        Task task = PORT.core().getUserTaskChain().getCurrentTask();
        say("Utility: core=" + PORT.running()
                + " paused=" + DAEMONS.paused
                + " barter=" + (DAEMONS.barterTarget == null ? "off" : DAEMONS.barterTarget + " gold=" + DAEMONS.barterGoldBuffer)
                + " sweep=" + (DAEMONS.sweepTargets.length == 0 ? "off" : Arrays.toString(DAEMONS.sweepTargets))
                + " task=" + (task == null ? "none" : task));
        return 1;
    }

    private static int stopAll() {
        DAEMONS.barterTarget = null;
        DAEMONS.sweepTargets = new ItemTarget[0];
        PORT.core().getUserTaskChain().cancel(PORT.core());
        say("Utility: stopped");
        return 1;
    }

    private static int pauseDaemons(boolean paused) {
        DAEMONS.paused = paused;
        say("Utility daemons: " + (paused ? "PAUSED" : "RUNNING"));
        return 1;
    }

    private static int run(Task task, String label) {
        ensureCore();
        PORT.core().runUserTask(task, () -> say(label + " done"), true);
        say(label);
        return 1;
    }

    private static void runDaemon(Task task, String label) {
        PORT.core().runUserTask(task, () -> say(label + " done"), true);
        say(label);
    }

    private static void ensureCore() {
        PORT.start();
    }

    private static boolean hasUserTask() {
        return PORT.core().getUserTaskChain().getCurrentTask() != null;
    }

    private static void cancelIfCurrent(Class<? extends Task> type) {
        Task task = PORT.core().getUserTaskChain().getCurrentTask();
        if (type.isInstance(task)) {
            PORT.core().getUserTaskChain().cancel(PORT.core());
        }
    }

    private static boolean targetMet(ItemTarget target) {
        return StorageHelper.itemTargetsMetInventory(PORT.core(), target);
    }

    private static boolean playerReady() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null;
    }

    private static ItemTarget target(String name, int count) {
        return AltoClefCompletions.resolveItemTarget(name, count);
    }

    private static String normalizeItemName(String name) {
        return AltoClefCompletions.normalizeItemName(name);
    }

    private static ItemTarget[] armorTargets(String spec) {
        return switch (normalizeItemName(spec)) {
            case "leather" -> armor(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
            case "gold", "golden" -> armor(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
            case "iron" -> armor(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            case "diamond" -> armor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
            case "netherite" -> armor(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
            default -> {
                ItemTarget target = target(spec, 1);
                boolean armor = !target.isEmpty() && Arrays.stream(target.getMatches()).allMatch(item -> item instanceof ArmorItem);
                yield armor ? new ItemTarget[]{target} : new ItemTarget[0];
            }
        };
    }

    private static ItemTarget[] armor(Item helmet, Item chest, Item legs, Item boots) {
        return new ItemTarget[]{new ItemTarget(helmet), new ItemTarget(chest), new ItemTarget(legs), new ItemTarget(boots)};
    }

    private static ItemTarget[] append(ItemTarget[] targets, ItemTarget target) {
        ItemTarget[] result = Arrays.copyOf(targets, targets.length + 1);
        result[targets.length] = target;
        return result;
    }

    private static int unknown(String item) {
        say("Unknown item: " + item);
        return 0;
    }

    private static void say(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }

    private static final class DaemonState {
        private ItemTarget barterTarget;
        private int barterGoldBuffer = 32;
        private ItemTarget[] sweepTargets = new ItemTarget[0];
        private boolean paused;

        private boolean anyActive() {
            return barterTarget != null || sweepTargets.length > 0;
        }
    }
}
