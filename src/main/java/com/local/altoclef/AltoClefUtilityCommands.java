package com.local.altoclef;

import adris.altoclef.AltoClef;
import adris.altoclef.AltoClefPort;
import adris.altoclef.platform.NeoForgeAltoClefMod;
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
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Arrays;

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
        NeoForge.EVENT_BUS.addListener(AltoClefUtilityCommands::clientTick);
    }

    /** Adds all utility sub-commands to the /colossuscraft root builder. */
    static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(barterBranch())
            .then(sweepBranch())
            .then(utilityBranch())
            .then(taskBranch())
            .then(Commands.literal("elytra").executes(ctx -> run(new GetElytraTask(), "Get elytra")))
            .then(portalBranch())
            .then(dimensionBranch())
            .then(locationBranch())
            .then(Commands.literal("sleep").executes(ctx -> run(new SleepThroughNightTask(), "Sleep through night")))
            .then(Commands.literal("setspawn").executes(ctx -> run(new PlaceBedAndSetSpawnTask(), "Set spawn")))
            .then(foodstockBranch())
            .then(gearBranch())
            .then(stashBranch())
            .then(avoidBlockBranch())
            .then(mineBranch());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mineBranch() {
        // Accepts: mine <block> [count]  OR  mine <count> <block1> <block2> ...
        return Commands.literal("mine")
            .then(Commands.argument("args", StringArgumentType.greedyString())
                .executes(ctx -> mineArgs(StringArgumentType.getString(ctx, "args").trim())));
    }

    // Blocks that only generate in the deep dark biome.
    private static final java.util.Set<String> DEEP_DARK_ORES = java.util.Set.of(
        "allthemodium_ore", "allthemodium:allthemodium_ore",
        "unobtainium_ore", "allthemodium:unobtainium_ore",
        "vibranium_ore",   "allthemodium:vibranium_ore"
    );

    private static ResourceKey<Biome> inferBiome(java.util.List<String> resolvedBlockIds) {
        for (String id : resolvedBlockIds) {
            String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            if (DEEP_DARK_ORES.contains(id) || DEEP_DARK_ORES.contains(path)) return Biomes.DEEP_DARK;
        }
        return null;
    }

    private static int mineArgs(String args) {
        String[] parts = args.split("\\s+");

        // Extract --biome <name> flag anywhere in the args
        ResourceKey<Biome> explicitBiome = null;
        java.util.List<String> filtered = new java.util.ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("--biome") && i + 1 < parts.length) {
                String biomeId = parts[++i];
                if (!biomeId.contains(":")) biomeId = "minecraft:" + biomeId;
                explicitBiome = ResourceKey.create(Registries.BIOME, ResourceLocation.tryParse(biomeId));
            } else {
                filtered.add(parts[i]);
            }
        }
        parts = filtered.toArray(new String[0]);

        int parsedCount = Integer.MAX_VALUE;
        int blockStart = 0;
        // If first token is a number, treat it as count
        try {
            parsedCount = Integer.parseInt(parts[0]);
            blockStart = 1;
        } catch (NumberFormatException ignored) {
            // Last token might be the count: "allthemodium_ore 64"
            if (parts.length >= 2) {
                try {
                    parsedCount = Integer.parseInt(parts[parts.length - 1]);
                    parts = Arrays.copyOf(parts, parts.length - 1);
                } catch (NumberFormatException ignored2) {}
            }
        }
        final int count = parsedCount;
        if (blockStart >= parts.length) {
            say("Usage: /cc mine <block> [count]  or  /cc mine <count> <block1> <block2> ...  [--biome <biome>]");
            return 0;
        }

        java.util.List<Block> blocks = new java.util.ArrayList<>();
        java.util.List<String> resolved = new java.util.ArrayList<>();
        for (int i = blockStart; i < parts.length; i++) {
            Block b = resolveBlock(parts[i]);
            if (b == null) { say("Unknown block: " + parts[i]); return 0; }
            blocks.add(b);
            resolved.add(BuiltInRegistries.BLOCK.getKey(b).toString());
        }

        adris.altoclef.util.MiningRequirement req = blocks.stream()
            .map(adris.altoclef.util.MiningRequirement::getMinimumRequirementForBlock)
            .max(java.util.Comparator.comparingInt(Enum::ordinal))
            .orElse(adris.altoclef.util.MiningRequirement.HAND);

        // Build item targets — one per block, sharing the total count
        adris.altoclef.util.ItemTarget[] targets = blocks.stream()
            .map(b -> new adris.altoclef.util.ItemTarget(b.asItem(), count))
            .toArray(adris.altoclef.util.ItemTarget[]::new);

        // --biome flag overrides; otherwise check if any block is a known deep-dark ore
        ResourceKey<Biome> biomePref = explicitBiome != null ? explicitBiome : inferBiome(resolved);

        return run(
            new adris.altoclef.tasks.resources.MineAndCollectTask(targets, blocks.toArray(new Block[0]), req, biomePref),
            "Mining " + String.join(", ", resolved) + (count == Integer.MAX_VALUE ? "" : " x" + count)
                + (biomePref != null ? " [prefers " + biomePref.location().getPath() + "]" : ""));
    }

    private static Block resolveBlock(String blockId) {
        if (blockId.contains(":")) {
            Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(blockId));
            return (b != null && b != net.minecraft.world.level.block.Blocks.AIR) ? b : null;
        }
        Block fallback = null;
        for (ResourceLocation key : BuiltInRegistries.BLOCK.keySet()) {
            if (key.getPath().equals(blockId)) {
                Block b = BuiltInRegistries.BLOCK.get(key);
                if (b == null || b == net.minecraft.world.level.block.Blocks.AIR) continue;
                if (!key.getNamespace().equals("minecraft")) return b;
                fallback = b;
            }
        }
        return fallback;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> avoidBlockBranch() {
        return Commands.literal("avoidblock")
            .then(Commands.literal("targeted").executes(ctx -> avoidBlockTargeted()))
            .then(Commands.literal("add")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> avoidBlockAdd(StringArgumentType.getString(ctx, "id")))))
            .then(Commands.literal("remove")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> avoidBlockRemove(StringArgumentType.getString(ctx, "id")))))
            .then(Commands.literal("list").executes(ctx -> avoidBlockList()))
            // bare /cc avoidblock <id> also works
            .then(Commands.argument("id", StringArgumentType.word())
                .executes(ctx -> avoidBlockAdd(StringArgumentType.getString(ctx, "id"))));
    }

    private static int avoidBlockTargeted() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr && mc.level != null) {
            net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(bhr.getBlockPos());
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            boolean added = BlockBreakBlacklist.add(id);
            say(added ? "Blocked from mining: " + id : "Already blocked: " + id);
        } else {
            say("Not looking at a block.");
        }
        return 1;
    }

    private static int avoidBlockAdd(String id) {
        boolean added = BlockBreakBlacklist.add(id);
        say(added ? "Blocked from mining: " + id : "Already blocked: " + id);
        return 1;
    }

    private static int avoidBlockRemove(String id) {
        boolean removed = BlockBreakBlacklist.remove(id);
        say(removed ? "Unblocked: " + id : "Not in blacklist: " + id);
        return 1;
    }

    private static int avoidBlockList() {
        java.util.Set<String> list = BlockBreakBlacklist.list();
        if (list.isEmpty()) {
            say("Block blacklist is empty.");
        } else {
            say("Blocked blocks (" + list.size() + "): " + String.join(", ", list));
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> barterBranch() {
        return Commands.literal("barter")
            .then(Commands.literal("stop").executes(ctx -> barterDaemon(false)))
            .then(Commands.literal("status").executes(ctx -> utilityStatus()))
            .then(Commands.literal("daemon")
                .then(Commands.literal("off").executes(ctx -> barterDaemon(false)))
                .then(Commands.literal("on")
                    .then(Commands.argument("query", StringArgumentType.greedyString())
                        .suggests(AltoClefCompletions::suggestItems)
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "query").trim();
                            String[] p = raw.split("\\s+", 3);
                            int count = p.length > 1 ? parseIntOr(p[1], 16) : 16;
                            int gold  = p.length > 2 ? parseIntOr(p[2], 32) : 32;
                            return barterDaemon(p[0], count, gold);
                        }))))
            .then(Commands.argument("query", StringArgumentType.greedyString())
                .suggests(AltoClefCompletions::suggestItems)
                .executes(ctx -> {
                    String raw = StringArgumentType.getString(ctx, "query").trim();
                    String[] p = raw.split("\\s+", 3);
                    String item = p[0];
                    int count = p.length > 1 ? parseIntOr(p[1], 16) : 16;
                    int gold  = p.length > 2 ? parseIntOr(p[2], 32) : 32;
                    return barter(item, count, gold);
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sweepBranch() {
        return Commands.literal("sweep")
            .then(Commands.literal("off").executes(ctx -> sweepOff()))
            .then(Commands.literal("clear").executes(ctx -> sweepOff()))
            .then(Commands.literal("status").executes(ctx -> utilityStatus()))
            .then(Commands.literal("once")
                .then(Commands.argument("query", StringArgumentType.greedyString())
                    .suggests(AltoClefCompletions::suggestItems)
                    .executes(ctx -> {
                        String[] p = StringArgumentType.getString(ctx, "query").trim().split("\\s+", 2);
                        return pickup(p[0], p.length > 1 ? parseIntOr(p[1], 1) : 1);
                    })))
            .then(Commands.literal("on")
                .then(Commands.argument("query", StringArgumentType.greedyString())
                    .suggests(AltoClefCompletions::suggestItems)
                    .executes(ctx -> {
                        String[] p = StringArgumentType.getString(ctx, "query").trim().split("\\s+", 2);
                        return sweepAdd(p[0], p.length > 1 ? parseIntOr(p[1], 1) : 1, true);
                    })))
            .then(Commands.literal("add")
                .then(Commands.argument("query", StringArgumentType.greedyString())
                    .suggests(AltoClefCompletions::suggestItems)
                    .executes(ctx -> {
                        String[] p = StringArgumentType.getString(ctx, "query").trim().split("\\s+", 2);
                        return sweepAdd(p[0], p.length > 1 ? parseIntOr(p[1], 1) : 1, false);
                    })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> utilityBranch() {
        return Commands.literal("utility")
            .then(Commands.literal("status").executes(ctx -> utilityStatus()))
            .then(Commands.literal("stop").executes(ctx -> stopAll()))
            .then(Commands.literal("pause").executes(ctx -> pauseDaemons(true)))
            .then(Commands.literal("resume").executes(ctx -> pauseDaemons(false)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> taskBranch() {
        return Commands.literal("task")
            .then(Commands.literal("loot")
                .then(Commands.literal("ruined_portals").executes(ctx -> run(new RavageRuinedPortalsTask(), "Loot ruined portals")))
                .then(Commands.literal("desert_temples").executes(ctx -> run(new RavageDesertTemplesTask(), "Loot desert temples"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> portalBranch() {
        return Commands.literal("portal")
            .then(Commands.literal("build").executes(ctx -> run(new ConstructNetherPortalTask(), "Build Nether portal")))
            .then(Commands.literal("nether").executes(ctx -> run(new ConstructNetherPortalTask(), "Build Nether portal")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dimensionBranch() {
        return Commands.literal("dimension")
            .then(Commands.literal("nether").executes(ctx -> run(new DefaultGoToDimensionTask(Dimension.NETHER), "Go to Nether")))
            .then(Commands.literal("overworld").executes(ctx -> run(new DefaultGoToDimensionTask(Dimension.OVERWORLD), "Go to Overworld")))
            .then(Commands.literal("end").executes(ctx -> run(new DefaultGoToDimensionTask(Dimension.END), "Go to End")))
            .then(Commands.literal("stronghold").executes(ctx -> run(new GoToStrongholdPortalTask(12), "Go to stronghold portal")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> locationBranch() {
        return Commands.literal("location")
            .then(Commands.argument("target", StringArgumentType.word())
                .suggests(AltoClefCompletions::suggestLocations)
                .executes(ctx -> location(StringArgumentType.getString(ctx, "target"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> foodstockBranch() {
        return Commands.literal("foodstock")
            .executes(ctx -> run(new CollectFoodTask(60), "Stock food"))
            .then(Commands.argument("units", IntegerArgumentType.integer(1))
                .executes(ctx -> run(new CollectFoodTask(IntegerArgumentType.getInteger(ctx, "units")), "Stock food")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gearBranch() {
        return Commands.literal("gear")
            .then(Commands.literal("armor")
                .then(Commands.argument("tier_or_item", StringArgumentType.word())
                    .suggests(AltoClefCompletions::suggestArmor)
                    .executes(ctx -> armor(StringArgumentType.getString(ctx, "tier_or_item")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stashBranch() {
        var z1Arg = Commands.argument("z1", IntegerArgumentType.integer())
            .executes(ctx -> stashRange(
                IntegerArgumentType.getInteger(ctx, "x0"),
                IntegerArgumentType.getInteger(ctx, "y0"),
                IntegerArgumentType.getInteger(ctx, "z0"),
                IntegerArgumentType.getInteger(ctx, "x1"),
                IntegerArgumentType.getInteger(ctx, "y1"),
                IntegerArgumentType.getInteger(ctx, "z1"),
                ItemTarget.EMPTY))
            .then(Commands.argument("query", StringArgumentType.greedyString())
                .suggests(AltoClefCompletions::suggestItems)
                .executes(ctx -> {
                    String[] p = StringArgumentType.getString(ctx, "query").trim().split("\\s+", 2);
                    return stashRange(
                        IntegerArgumentType.getInteger(ctx, "x0"),
                        IntegerArgumentType.getInteger(ctx, "y0"),
                        IntegerArgumentType.getInteger(ctx, "z0"),
                        IntegerArgumentType.getInteger(ctx, "x1"),
                        IntegerArgumentType.getInteger(ctx, "y1"),
                        IntegerArgumentType.getInteger(ctx, "z1"),
                        target(p[0], p.length > 1 ? parseIntOr(p[1], 1) : 1));
                }));
        return Commands.literal("stash")
            .then(Commands.literal("open")
                .executes(ctx -> run(new StoreInAnyContainerTask(false), "Deposit inventory in open container"))
                .then(Commands.argument("query", StringArgumentType.greedyString())
                    .suggests(AltoClefCompletions::suggestItems)
                    .executes(ctx -> {
                        String[] p = StringArgumentType.getString(ctx, "query").trim().split("\\s+", 2);
                        return deposit(p[0], p.length > 1 ? parseIntOr(p[1], 1) : 1);
                    })))
            .then(Commands.literal("range")
                .then(Commands.argument("x0", IntegerArgumentType.integer())
                    .then(Commands.argument("y0", IntegerArgumentType.integer())
                        .then(Commands.argument("z0", IntegerArgumentType.integer())
                            .then(Commands.argument("x1", IntegerArgumentType.integer())
                                .then(Commands.argument("y1", IntegerArgumentType.integer())
                                    .then(z1Arg)))))));
    }

    private static int parseIntOr(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
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

    private static ItemTarget[] armorTargets(String spec) {
        return switch (AltoClefCompletions.normalizeItemName(spec)) {
            case "leather" -> armor(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
            case "gold", "golden" -> armor(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
            case "iron" -> armor(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            case "diamond" -> armor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
            case "netherite" -> armor(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
            default -> {
                ItemTarget t = target(spec, 1);
                boolean isArmor = !t.isEmpty() && Arrays.stream(t.getMatches()).allMatch(item -> item instanceof ArmorItem);
                yield isArmor ? new ItemTarget[]{t} : new ItemTarget[0];
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
