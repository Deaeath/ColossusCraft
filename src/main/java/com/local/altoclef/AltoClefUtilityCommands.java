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
            .then(mineBranch())
            .then(scanBranch());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mineBranch() {
        // Accepts: mine <block> [count]  OR  mine <count> <block1> <block2> ...
        return Commands.literal("mine")
            .then(Commands.argument("args", StringArgumentType.greedyString())
                .suggests(AltoClefUtilityCommands::suggestMineArgs)
                .executes(ctx -> mineArgs(StringArgumentType.getString(ctx, "args").trim())));
    }

    /** Suggests block names for each whitespace-separated token in the mine greedy arg. */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMineArgs(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        // Skip numeric tokens and --biome flag values so we suggest blocks, not counts
        int lastSpace = remaining.lastIndexOf(' ');
        if (lastSpace >= 0) {
            String lastToken = remaining.substring(lastSpace + 1);
            // If previous token was --biome, suggest biomes not blocks
            String trimmed = remaining.substring(0, lastSpace).trim();
            if (trimmed.endsWith("--biome")) {
                return AltoClefCompletions.suggestLocations(ctx,
                        builder.createOffset(builder.getStart() + lastSpace + 1));
            }
            builder = builder.createOffset(builder.getStart() + lastSpace + 1);
        }
        return AltoClefCompletions.suggestBlocks(ctx, builder);
    }

    // Companion blocks to auto-add when mining modded ores that have multiple block variants.
    // allthemodium generates as allthemodium_ore (stone) OR allthemodium_slate_ore (deepslate).
    // In the mining dimension, only the slate variant generates (Y=129 deepslate layer).
    private static final java.util.Map<String, java.util.List<String>> ORE_COMPANIONS = java.util.Map.of(
        "allthemodium:allthemodium_ore",       java.util.List.of("allthemodium:allthemodium_slate_ore"),
        "allthemodium:allthemodium_slate_ore", java.util.List.of("allthemodium:allthemodium_ore"),
        "allthemodium:vibranium_ore",          java.util.List.of("allthemodium:other_vibranium_ore"),
        "allthemodium:other_vibranium_ore",    java.util.List.of("allthemodium:vibranium_ore"),
        "allthemodium:unobtainium_ore",        java.util.List.of()
    );

    /** Expand a resolved block ID list to include known companion ore blocks (same ore, different substrate). */
    private static java.util.List<String> addCompanions(java.util.List<String> ids) {
        java.util.LinkedHashSet<String> expanded = new java.util.LinkedHashSet<>(ids);
        for (String id : ids) {
            java.util.List<String> companions = ORE_COMPANIONS.get(id);
            if (companions != null) expanded.addAll(companions);
        }
        return new java.util.ArrayList<>(expanded);
    }

    private static ResourceKey<Biome> inferBiome(java.util.List<String> resolvedBlockIds) {
        // ATM ores generate in the allthemodium:mining dimension, not deep dark.
        // No automatic biome inference needed — the bot follows dimension routing separately.
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

        java.util.List<String> resolved = new java.util.ArrayList<>();
        for (int i = blockStart; i < parts.length; i++) {
            Block b = resolveBlock(parts[i]);
            if (b == null) { say("Unknown block: " + parts[i]); return 0; }
            resolved.add(BuiltInRegistries.BLOCK.getKey(b).toString());
        }
        // Expand with companion ore variants (e.g. allthemodium_ore ↔ allthemodium_slate_ore).
        resolved = addCompanions(resolved);

        java.util.List<Block> blocks = new java.util.ArrayList<>();
        for (String id : resolved) {
            net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(id);
            if (loc == null) continue;
            Block b = BuiltInRegistries.BLOCK.get(loc);
            if (b != null && b != net.minecraft.world.level.block.Blocks.AIR) blocks.add(b);
        }

        adris.altoclef.util.MiningRequirement req = blocks.stream()
            .map(adris.altoclef.util.MiningRequirement::getMinimumRequirementForBlock)
            .max(java.util.Comparator.comparingInt(Enum::ordinal))
            .orElse(adris.altoclef.util.MiningRequirement.HAND);

        // Build item targets from likely drops, not just the ore block item.
        // Mod ores usually drop raw items, so targeting block.asItem() can make /cc mine stop early or ignore drops.
        adris.altoclef.util.ItemTarget[] targets = mineOutputTargets(resolved, blocks, count);

        // --biome flag overrides; otherwise check if any block is a known deep-dark ore
        ResourceKey<Biome> biomePref = explicitBiome != null ? explicitBiome : inferBiome(resolved);

        return run(
            new adris.altoclef.tasks.resources.MineAndCollectTask(targets, blocks.toArray(new Block[0]), req, biomePref),
            "Mining " + String.join(", ", resolved) + (count == Integer.MAX_VALUE ? "" : " x" + count)
                + (biomePref != null ? " [prefers " + biomePref.location().getPath() + "]" : ""));
    }

    private static adris.altoclef.util.ItemTarget[] mineOutputTargets(java.util.List<String> ids, java.util.List<Block> blocks, int count) {
        java.util.LinkedHashSet<Item> items = new java.util.LinkedHashSet<>();
        for (Block block : blocks) {
            Item item = block.asItem();
            if (item != null && item != Items.AIR) items.add(item);
        }
        for (String id : ids) addLikelyMineDrops(id, items);
        if (items.isEmpty()) {
            for (Block block : blocks) {
                Item item = block.asItem();
                if (item != null) items.add(item);
            }
        }
        return new adris.altoclef.util.ItemTarget[]{
            new adris.altoclef.util.ItemTarget(items.toArray(Item[]::new), count)
        };
    }

    private static void addLikelyMineDrops(String blockId, java.util.Set<Item> out) {
        ResourceLocation loc = ResourceLocation.tryParse(blockId);
        if (loc == null) return;
        String ns = loc.getNamespace();
        String path = loc.getPath();

        if (ns.equals("allthemodium")) {
            if (path.contains("allthemodium")) {
                addItem(out, "allthemodium:raw_allthemodium");
                addItem(out, "allthemodium:allthemodium_ingot");
            } else if (path.contains("vibranium")) {
                addItem(out, "allthemodium:raw_vibranium");
                addItem(out, "allthemodium:vibranium_ingot");
            } else if (path.contains("unobtainium")) {
                addItem(out, "allthemodium:raw_unobtainium");
                addItem(out, "allthemodium:unobtainium_ingot");
            }
            return;
        }

        switch (blockId) {
            case "minecraft:coal_ore", "minecraft:deepslate_coal_ore" -> addItem(out, "minecraft:coal");
            case "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore" -> addItem(out, "minecraft:diamond");
            case "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore" -> addItem(out, "minecraft:emerald");
            case "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore" -> addItem(out, "minecraft:lapis_lazuli");
            case "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore" -> addItem(out, "minecraft:redstone");
            case "minecraft:nether_gold_ore", "minecraft:gold_ore", "minecraft:deepslate_gold_ore" -> addItem(out, "minecraft:raw_gold");
            case "minecraft:iron_ore", "minecraft:deepslate_iron_ore" -> addItem(out, "minecraft:raw_iron");
            case "minecraft:copper_ore", "minecraft:deepslate_copper_ore" -> addItem(out, "minecraft:raw_copper");
            case "minecraft:nether_quartz_ore" -> addItem(out, "minecraft:quartz");
            default -> {
                if (path.endsWith("_ore")) {
                    String base = path.substring(0, path.length() - "_ore".length());
                    if (base.startsWith("deepslate_")) base = base.substring("deepslate_".length());
                    if (base.startsWith("other_")) base = base.substring("other_".length());
                    addItem(out, ns + ":raw_" + base);
                    addItem(out, ns + ":" + base + "_ingot");
                    addItem(out, ns + ":" + base);
                }
            }
        }
    }

    private static void addItem(java.util.Set<Item> out, String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) return;
        Item item = BuiltInRegistries.ITEM.get(loc);
        if (item != null && item != Items.AIR) out.add(item);
    }

    private static Block resolveBlock(String blockId) {
        return AltoClefCompletions.resolveBlock(blockId);
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


    // Auto-save chunk cache every ~5 minutes (6000 ticks) while a scan is active.
    private static int _cacheSaveTick = 0;
    private static void clientTick(ClientTickEvent.Post event) {
        // Periodic chunk cache save (every 6000 ticks ≈ 5 min)
        if (++_cacheSaveTick >= 6000) {
            _cacheSaveTick = 0;
            adris.altoclef.AltoClef mod = PORT.core();
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mod != null && mc.level != null && mod.getBlockTracker().getChunkCache().countScanned() > 0) {
                String dim = mc.level.dimension().location().getPath();
                adris.altoclef.trackers.ChunkCachePersistence.save(
                    mod.getBlockTracker().getChunkCache(), dim, mod.getBlockTracker().getTrackedBlockIds());
            }
        }
        if (!DAEMONS.anyActive() || DAEMONS.paused || tick % 10 != 0 || !playerReady()) {
            tick++;
            return;
        }
        tick++;
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

    private static LiteralArgumentBuilder<CommandSourceStack> scanBranch() {
        return Commands.literal("scan")
            .executes(ctx -> scanStatus())
            .then(Commands.literal("status").executes(ctx -> scanStatus()))
            .then(Commands.literal("predict")
                // /cc scan predict             → auto-detect seed, 512 chunk radius
                .executes(ctx -> scanPredictAuto(512, OrePredictor.defaultFeatureIndex))
                .then(Commands.argument("seed", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                    .executes(ctx -> scanPredict(com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "seed"), 512, OrePredictor.defaultFeatureIndex))
                    .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(1, 4096))
                        .executes(ctx -> scanPredict(
                            com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "seed"),
                            IntegerArgumentType.getInteger(ctx, "radiusChunks"),
                            OrePredictor.defaultFeatureIndex))
                        .then(Commands.argument("featureIdx", IntegerArgumentType.integer(0, 256))
                            .executes(ctx -> scanPredict(
                                com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "seed"),
                                IntegerArgumentType.getInteger(ctx, "radiusChunks"),
                                IntegerArgumentType.getInteger(ctx, "featureIdx")))))))
            .then(Commands.literal("calibrate")
                .then(Commands.literal("exact")
                    .then(Commands.argument("seed", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                        .then(Commands.argument("knownOreX", IntegerArgumentType.integer())
                            .then(Commands.argument("knownOreY", IntegerArgumentType.integer())
                                .then(Commands.argument("knownOreZ", IntegerArgumentType.integer())
                                    .executes(ctx -> scanCalibrate(
                                        com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "seed"),
                                        IntegerArgumentType.getInteger(ctx, "knownOreX"),
                                        IntegerArgumentType.getInteger(ctx, "knownOreY"),
                                        IntegerArgumentType.getInteger(ctx, "knownOreZ"))))))))
                // /cc scan calibrate <x> <z>  → auto-detect seed
                .then(Commands.argument("knownOreX", IntegerArgumentType.integer())
                    .then(Commands.argument("knownOreZ", IntegerArgumentType.integer())
                        .executes(ctx -> scanCalibrateAuto(
                            IntegerArgumentType.getInteger(ctx, "knownOreX"),
                            IntegerArgumentType.getInteger(ctx, "knownOreZ")))))
                .then(Commands.argument("seed", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                    .then(Commands.argument("knownOreX", IntegerArgumentType.integer())
                        .then(Commands.argument("knownOreZ", IntegerArgumentType.integer())
                            .executes(ctx -> scanCalibrate(
                                com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "seed"),
                                IntegerArgumentType.getInteger(ctx, "knownOreX"),
                                IntegerArgumentType.getInteger(ctx, "knownOreZ"))))
                        .then(Commands.argument("knownOreY", IntegerArgumentType.integer())
                            .then(Commands.argument("knownOreZ2", IntegerArgumentType.integer())
                                .executes(ctx -> scanCalibrate(
                                    com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "seed"),
                                    IntegerArgumentType.getInteger(ctx, "knownOreX"),
                                    IntegerArgumentType.getInteger(ctx, "knownOreY"),
                                    IntegerArgumentType.getInteger(ctx, "knownOreZ2"))))))))
            .then(Commands.literal("save").executes(ctx -> scanSave()))
            .then(Commands.literal("list").executes(ctx -> scanList()))
            .then(Commands.literal("overlay")
                .executes(ctx -> overlayToggle())
                .then(Commands.literal("on").executes(ctx -> overlaySet(true)))
                .then(Commands.literal("off").executes(ctx -> overlaySet(false))))
            .then(Commands.literal("h").then(Commands.argument("blocks", IntegerArgumentType.integer(8, 512))
                .executes(ctx -> {
                    NeoForgeAltoClefMod.port().core().getModSettings().setBlockScanHorizontalRange(IntegerArgumentType.getInteger(ctx, "blocks"));
                    return scanStatus();
                })))
            .then(Commands.literal("v").then(Commands.argument("blocks", IntegerArgumentType.integer(8, 512))
                .executes(ctx -> {
                    NeoForgeAltoClefMod.port().core().getModSettings().setBlockScanVerticalRange(IntegerArgumentType.getInteger(ctx, "blocks"));
                    return scanStatus();
                })))
            .then(Commands.literal("interval").then(Commands.argument("ms", IntegerArgumentType.integer(500, 30000))
                .executes(ctx -> {
                    adris.altoclef.trackers.BlockTracker.SCAN_INTERVAL_MS = IntegerArgumentType.getInteger(ctx, "ms");
                    return scanStatus();
                })));
    }

    private static int scanPredictAuto(int radiusChunks, int featureIdx) {
        say("Auto seed detection not yet implemented. Use: /cc scan predict <seed> [radius] [featureIdx]");
        return 0;
    }

    private static int scanCalibrateAuto(int knownOreX, int knownOreZ) {
        say("Auto seed detection not yet implemented. Use: /cc scan calibrate <seed> <x> <z>");
        return 0;
    }

    private static int scanPredict(long worldSeed, int radiusChunks, int featureIdx) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) { say("Not in a world."); return 0; }
        int originCx = (int) mc.player.getX() >> 4;
        int originCz = (int) mc.player.getZ() >> 4;
        say("Predicting ore in " + (radiusChunks * 2 + 1) + "² chunk area (featureIdx=" + featureIdx + ")...");

        java.util.List<net.minecraft.core.BlockPos> hits =
            OrePredictor.scan(worldSeed, originCx, originCz, radiusChunks, featureIdx);

        adris.altoclef.trackers.BlockTracker bt = NeoForgeAltoClefMod.port().core().getBlockTracker();
        adris.altoclef.trackers.ChunkScanCache cache = bt.getChunkCache();
        int prepopulated = 0;
        int total = (radiusChunks * 2 + 1) * (radiusChunks * 2 + 1);
        for (int dcx = -radiusChunks; dcx <= radiusChunks; dcx++) {
            for (int dcz = -radiusChunks; dcz <= radiusChunks; dcz++) {
                cache.mark(originCx + dcx, originCz + dcz, adris.altoclef.trackers.ChunkScanCache.State.CLEAN);
                prepopulated++;
            }
        }
        for (net.minecraft.core.BlockPos p : hits) {
            cache.mark(p.getX() >> 4, p.getZ() >> 4, adris.altoclef.trackers.ChunkScanCache.State.HAS_ORE);
        }
        say("Found " + hits.size() + " ore chunks out of " + total + " ("
            + String.format("%.2f", hits.size() * 100.0 / total) + "%)");
        say("Pre-populated cache — spiral will skip the " + (total - hits.size()) + " empty chunks.");
        if (!hits.isEmpty()) {
            net.minecraft.core.BlockPos nearest = hits.get(0);
            say("Nearest predicted ore: " + nearest.getX() + ", " + nearest.getY() + ", " + nearest.getZ()
                + "  (chunk " + (nearest.getX() >> 4) + ", " + (nearest.getZ() >> 4) + ")");
            say("Use: /cc nav goto " + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ());
        }
        OrePredictor.defaultFeatureIndex = featureIdx;
        return 1;
    }

    private static int scanCalibrate(long worldSeed, int knownOreX, int knownOreZ) {
        say("Brute-forcing feature index for seed=" + worldSeed
            + " known ore chunk (" + (knownOreX >> 4) + ", " + (knownOreZ >> 4) + ")...");
        int idx = OrePredictor.calibrate(worldSeed, knownOreX, knownOreZ);
        return finishScanCalibrate(worldSeed, idx);
    }

    private static int scanCalibrate(long worldSeed, int knownOreX, int knownOreY, int knownOreZ) {
        say("Brute-forcing feature index for seed=" + worldSeed
            + " known ore " + knownOreX + ", " + knownOreY + ", " + knownOreZ
            + " chunk (" + (knownOreX >> 4) + ", " + (knownOreZ >> 4) + ")...");
        int idx = OrePredictor.calibrate(worldSeed, knownOreX, knownOreY, knownOreZ);
        return finishScanCalibrate(worldSeed, idx);
    }

    private static int finishScanCalibrate(long worldSeed, int idx) {
        if (idx < 0) {
            say("Could not find a matching feature index (0-256). "
                + "Check that the ore coordinates are correct and the seed matches.");
        } else {
            OrePredictor.defaultFeatureIndex = idx;
            say("Calibrated! Feature index = " + idx
                + ". Now use /cc scan predict " + worldSeed + " to pre-populate the cache.");
        }
        return 1;
    }

    private static int scanList() {
        adris.altoclef.AltoClef mod = NeoForgeAltoClefMod.port().core();
        if (mod == null) { say("Bot not loaded."); return 0; }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        // Force a fresh scan right now so mined blocks/stale cache entries don't get written as ore.
        mod.getBlockTracker().forceRefresh();
        // Collect all tracked block types and get their known positions
        java.util.List<net.minecraft.world.level.block.Block> trackedBlocks = new java.util.ArrayList<>();
        for (String id : mod.getBlockTracker().getTrackedBlockIds()) {
            net.minecraft.world.level.block.Block b = resolveBlock(id);
            if (b != null) trackedBlocks.add(b);
        }
        java.util.List<net.minecraft.core.BlockPos> all = trackedBlocks.isEmpty()
            ? java.util.List.of()
            : mod.getBlockTracker().getKnownLocations(trackedBlocks.toArray(new net.minecraft.world.level.block.Block[0]));
        if (!all.isEmpty()) {
            java.util.Set<net.minecraft.world.level.block.Block> trackedSet = new java.util.HashSet<>(trackedBlocks);
            all = all.stream()
                .filter(p -> mod.getWorld() != null && trackedSet.contains(mod.getWorld().getBlockState(p).getBlock()))
                .toList();
        }
        if (all.isEmpty()) {
            say("No tracked ore in current scan range. Make sure /cc mine is running and you're near ore.");
            return 1;
        }
        // Sort by distance from player
        net.minecraft.world.phys.Vec3 pp = mc.player != null ? mc.player.position() : net.minecraft.world.phys.Vec3.ZERO;
        all.sort(java.util.Comparator.comparingDouble(p -> p.getCenter().distanceToSqr(pp)));
        // Write to file
        java.nio.file.Path out = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
            .resolve("colossuscraft").resolve("ore_locations.txt");
        try {
            java.nio.file.Files.createDirectories(out.getParent());
            java.util.List<String> lines = new java.util.ArrayList<>();
            lines.add("# Tracked ore positions at " + new java.util.Date());
            if (mc.level != null) lines.add("# Dimension " + mc.level.dimension().location());
            for (net.minecraft.core.BlockPos p : all) {
                net.minecraft.world.level.block.Block b = mod.getWorld().getBlockState(p).getBlock();
                String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString();
                lines.add(id + " " + p.getX() + " " + p.getY() + " " + p.getZ());
            }
            java.nio.file.Files.write(out, lines);
            say("Saved " + all.size() + " ore positions to " + out);
            // Print nearest 5 in chat
            say("Nearest " + Math.min(5, all.size()) + ":");
            for (int i = 0; i < Math.min(5, all.size()); i++) {
                net.minecraft.core.BlockPos p = all.get(i);
                say("  " + p.getX() + " " + p.getY() + " " + p.getZ());
            }
        } catch (java.io.IOException e) {
            say("Failed to write: " + e.getMessage());
        }
        return 1;
    }

    private static int scanSave() {
        adris.altoclef.AltoClef mod = NeoForgeAltoClefMod.port().core();
        if (mod == null) { say("Bot not loaded."); return 0; }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        String dim = mc.level != null ? mc.level.dimension().location().getPath() : "unknown";
        adris.altoclef.trackers.ChunkCachePersistence.save(
            mod.getBlockTracker().getChunkCache(), dim, mod.getBlockTracker().getTrackedBlockIds());
        adris.altoclef.trackers.ChunkScanCache cache = mod.getBlockTracker().getChunkCache();
        say("Saved " + cache.countScanned() + " chunks ("
            + cache.countClean() + " empty, " + cache.countHasOre() + " with ore) to disk.");
        return 1;
    }

    private static int overlayToggle() {
        return overlaySet(!ChunkOverlayRenderer.enabled);
    }

    private static int overlaySet(boolean on) {
        ChunkOverlayRenderer.enabled = on;
        if (on) {
            adris.altoclef.trackers.BlockTracker bt = NeoForgeAltoClefMod.port().core().getBlockTracker();
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            int y = mc.player != null ? mc.level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) mc.player.getX(), (int) mc.player.getZ()) : 316;
            ChunkOverlayRenderer.setCache(bt.getChunkCache(), y);
        }
        say("Chunk overlay: " + (on ? "ON (green=ore, red=empty)" : "OFF"));
        return 1;
    }

    private static int scanStatus() {
        adris.altoclef.Settings s = NeoForgeAltoClefMod.port().core().getModSettings();
        int h = s.getBlockScanHorizontalRange(), v = s.getBlockScanVerticalRange();
        long total = (long)(h * 2 + 1) * (v + 1) * (h * 2 + 1);
        say("Scan: h=" + h + " v=" + v + " interval=" + adris.altoclef.trackers.BlockTracker.SCAN_INTERVAL_MS
            + "ms  box=" + total/1_000_000 + "M blocks/scan");

        // Seed note: ClientLevel doesn't expose the world seed. Use /seed in-game or check server console.

        adris.altoclef.trackers.ChunkScanCache cache =
            NeoForgeAltoClefMod.port().core().getBlockTracker().getChunkCache();
        int scanned = cache.countScanned(), clean = cache.countClean(), hasOre = cache.countHasOre();
        if (scanned > 0) {
            say("Chunks: " + scanned + " scanned | " + clean + " empty | " + hasOre + " has-ore");
        } else {
            say("Chunks: none scanned yet — start mining to populate cache");
        }
        say("Overlay: " + (ChunkOverlayRenderer.enabled ? "ON" : "OFF") + "  (/cc scan overlay)");
        return 1;
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
