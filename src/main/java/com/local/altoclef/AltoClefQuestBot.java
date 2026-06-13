package com.local.altoclef;

import adris.altoclef.AltoClefPort;
import com.local.baritoneautoeat.BaritoneAutoEat;
import adris.altoclef.platform.NeoForgeAltoClefMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.net.ClaimAllRewardsMessage;
import dev.ftb.mods.ftbquests.net.SubmitTaskMessage;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Mod(value = AltoClefQuestBot.MOD_ID, dist = Dist.CLIENT)
public final class AltoClefQuestBot {
    public static final String MOD_ID = "colossuscraft_atm10";

    private static boolean enabled;
    private static int tick;
    private static final AltoClefPort upstreamPort = NeoForgeAltoClefMod.port();
    private static long activeTaskId = Long.MIN_VALUE;
    private static String activeMineTarget = "";
    private static GoalMode goalMode = GoalMode.ATM_STAR;
    private static int ftbPayloadCooldown;
    private static final ArrayDeque<CustomPacketPayload> ftbPayloadQueue = new ArrayDeque<>();
    private static int pendingCraftMenuId = -1;
    private static int pendingCraftResultSlot = -1;
    private static int pendingCraftDelay;
    private static final String ATM_STAR = "allthetweaks:atm_star";
    private static final String AWAKENED_ALLOY_BLOCK = "allthemodium:unobtainium_vibranium_alloy_block#awakened";
    private static final String INFUSED_PATRICK_STAR = "allthetweaks:patrick_star#infused";
    private static final Map<String, StarRecipe> STAR_RECIPES = buildStarRecipes();

    public AltoClefQuestBot(IEventBus modBus) {
        EmergencyHome.init();
        InventoryView.init(modBus);
        AltoClefUtilityCommands.init();
        BowAimbot.init();
        NeoForge.EVENT_BUS.addListener(AltoClefQuestBot::registerCommands);
        NeoForge.EVENT_BUS.addListener(AltoClefQuestBot::clientTick);
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("colossuscraft")
                // Core controls (replaces /altoclef)
                .then(Commands.literal("on").executes(ctx -> coreOn()))
                .then(Commands.literal("off").executes(ctx -> coreOff()))
                .then(Commands.literal("status").executes(ctx -> coreStatus()))
                .then(Commands.literal("stop").executes(ctx -> stop()))
                .then(Commands.literal("help").executes(ctx -> coreExec("help")))
                .then(Commands.literal("exec").then(Commands.argument("command", StringArgumentType.greedyString()).executes(ctx -> coreExec(StringArgumentType.getString(ctx, "command")))))
                .then(Commands.literal("nav").then(Commands.argument("command", StringArgumentType.greedyString()).executes(ctx -> baritone(StringArgumentType.getString(ctx, "command")))))

                // ATM10 Sub-module
                .then(Commands.literal("atm")
                        .then(Commands.literal("on").executes(ctx -> setEnabled(true)))
                        .then(Commands.literal("off").executes(ctx -> setEnabled(false)))
                        .then(Commands.literal("toggle").executes(ctx -> setEnabled(!enabled)))
                        .then(Commands.literal("status").executes(ctx -> status()))
                        .then(Commands.literal("next").executes(ctx -> next()))
                        .then(Commands.literal("star").executes(ctx -> star()))
                        .then(Commands.literal("starplan").executes(ctx -> starPlan()))
                        .then(Commands.literal("snapshot").executes(ctx -> snapshot()))
                        .then(Commands.literal("assess").executes(ctx -> snapshot()))
                        .then(Commands.literal("audit").executes(ctx -> audit()))
                        .then(Commands.literal("submit").executes(ctx -> submitNow()))
                        .then(Commands.literal("claim").executes(ctx -> claimNow()))
                        .then(Commands.literal("goal")
                                .then(Commands.literal("star").executes(ctx -> setGoal(GoalMode.ATM_STAR)))
                                .then(Commands.literal("all").executes(ctx -> setGoal(GoalMode.ALL_QUESTS))))
                        .then(Commands.literal("altar").executes(ctx -> altar()))
                .then(Commands.literal("machines").executes(ctx -> machines()))) // Closes the "atm" literal

                // Bot functionality
                .then(Commands.literal("follow").then(Commands.argument("player", StringArgumentType.word())
                                .suggests(AltoClefCompletions::suggestPlayers)
                                .executes(ctx -> follow(StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("food")
                        .then(Commands.argument("units", IntegerArgumentType.integer(1)).executes(ctx -> food(IntegerArgumentType.getInteger(ctx, "units")))))
                .then(Commands.literal("come").executes(ctx -> come()))
                .then(Commands.literal("escape").executes(ctx -> escape()))
                .then(Commands.literal("autohunt")
                        .then(Commands.literal("on").executes(ctx -> setAutoHunt(true)))
                        .then(Commands.literal("off").executes(ctx -> setAutoHunt(false))))
                .then(Commands.literal("findchest")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .suggests(AltoClefCompletions::suggestItems)
                                .executes(ctx -> findChest(StringArgumentType.getString(ctx, "item"), false))
                                .then(Commands.literal("goto").executes(ctx -> findChest(StringArgumentType.getString(ctx, "item"), true)))))

                // Direct actions
                .then(Commands.literal("kill").then(Commands.argument("entity", StringArgumentType.string())
                        .suggests(AltoClefCompletions::suggestEntities)
                        .executes(ctx -> kill(StringArgumentType.getString(ctx, "entity")))))
                .then(Commands.literal("get").then(Commands.argument("item", StringArgumentType.word())
                        .suggests(AltoClefCompletions::suggestItems)
                        .executes(ctx -> get(StringArgumentType.getString(ctx, "item"), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> get(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("goto")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> gotoPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"))))))
                        .then(Commands.literal("entity")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(AltoClefCompletions::suggestEntities)
                                        .executes(ctx -> gotoEntity(StringArgumentType.getString(ctx, "type")))))
                        .then(Commands.literal("item")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests(AltoClefCompletions::suggestItems)
                                        .executes(ctx -> gotoItem(StringArgumentType.getString(ctx, "item")))))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(AltoClefCompletions::suggestPlayers)
                                .executes(ctx -> gotoPlayer(StringArgumentType.getString(ctx, "player")))));
        // Sub-modules
        root.then(EmergencyHome.command());
        root.then(InventoryView.command());
        AltoClefUtilityCommands.addCommands(root);
        root.then(BaritoneAutoEat.command());
        root.then(Commands.literal("warden")
                .then(Commands.literal("golems")
                        .executes(ctx -> wardenGolems(6))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 12))
                                .executes(ctx -> wardenGolems(IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("stop").executes(ctx -> wardenStop())));
        root.then(Commands.literal("bow")
                .then(Commands.literal("on").executes(ctx -> { BowAimbot.setEnabled(true); return 1; }))
                .then(Commands.literal("off").executes(ctx -> { BowAimbot.setEnabled(false); return 1; }))
                .then(Commands.literal("status").executes(ctx -> {
                    Minecraft.getInstance().player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("Bow aimbot: " + (BowAimbot.isEnabled() ? "ON" : "OFF")), false);
                    return 1;
                })));

        event.getDispatcher().register(root);
        event.getDispatcher().register(Commands.literal("cc").redirect(event.getDispatcher().getRoot().getChild("colossuscraft")));
        event.getDispatcher().register(Commands.literal("goto").redirect(event.getDispatcher().getRoot().getChild("colossuscraft").getChild("goto")));
    }

    private static int setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            runBaritone("stop");
            activeTaskId = Long.MIN_VALUE;
            activeMineTarget = "";
            ftbPayloadQueue.clear();
            clearPendingCraft();
        }
        say("ColossusCraft ATM10 quests: " + (enabled ? "ON" : "OFF") + " goal=" + goalMode.label);
        return 1;
    }

    private static int setGoal(GoalMode mode) {
        goalMode = mode;
        activeTaskId = Long.MIN_VALUE;
        activeMineTarget = "";
        say("ColossusCraft goal: " + mode.label);
        return 1;
    }

    private static int status() {
        QuestTarget target = findNextTarget();
        if (target == null) {
            QuestStep step = findNextQuestStep();
            say("ColossusCraft: " + (enabled ? "ON" : "OFF") + " goal=" + goalMode.label + " " + (step == null ? "no visible quest step" : step.action));
            return 1;
        }
        say("ColossusCraft: " + (enabled ? "ON" : "OFF") + " goal=" + goalMode.label + " next " + target.itemId + " " + target.progress + "/" + target.required + " quest=" + target.questTitle + " action=" + targetAction(target));
        return 1;
    }

    private static int next() {
        QuestTarget target = findNextTarget();
        if (target == null) {
            QuestStep step = findNextQuestStep();
            say(step == null ? "ColossusCraft: no visible quest step" : "ColossusCraft next: " + step.action + " quest=" + step.questTitle);
            return 0;
        }
        say("ColossusCraft next: " + target.itemId + " need " + Math.max(0L, target.required - target.progress) + " quest=" + target.questTitle + " action=" + targetAction(target));
        return 1;
    }

    private static int star() {
        goalMode = GoalMode.ATM_STAR;
        enabled = true;
        activeTaskId = Long.MIN_VALUE;
        activeMineTarget = "";
        int submitted = submitReadyTasks();
        if (submitted > 0) {
            claimAllRewards();
        }
        try {
            writeStarPlan();
        } catch (IOException e) {
            say("ATM Star plan failed: " + e.getMessage());
        }
        QuestTarget target = findNextTarget();
        StarBlocker blocker = findStarBlocker();
        if (target != null) {
            RouteGate gate = routeGateFor(target.itemId);
            if (gate != null) {
                say("ATM Star quest: " + target.itemId + " need " + Math.max(0L, target.required - target.progress) + " route=" + gate.action);
                driveRouteGate(target.taskId, gate);
                return 1;
            }
            List<String> mineTargets = autoMineTargets(target.itemId);
            if (mineTargets.isEmpty() && driveSpecialTarget(target)) {
                return 1;
            }
            say("ATM Star quest: " + target.itemId + " need " + Math.max(0L, target.required - target.progress) + " via " + (mineTargets.isEmpty() ? "craft/machine" : "mine " + String.join(",", mineTargets)));
            if (!mineTargets.isEmpty()) {
                runQuestMine(target, mineTargets);
            }
            return 1;
        }
        QuestStep step = findNextQuestStep();
        if (step != null) {
            driveQuestStep(step);
            return 1;
        }
        if (blocker == null) {
            say("ATM Star blocker: none in carried inventory");
            return 1;
        }
        say("ATM Star blocker: " + describeBlocker(blocker));
        driveStarGoal();
        return 1;
    }

    private static int starPlan() {
        try {
            say("ATM Star plan: " + writeStarPlan());
        } catch (IOException e) {
            say("ATM Star plan failed: " + e.getMessage());
            return 0;
        }
        return 1;
    }

    private static int snapshot() {
        String report = buildSnapshotReport();
        Path output = Minecraft.getInstance().gameDirectory.toPath().resolve("colossuscraft-snapshot.txt");
        try {
            Files.writeString(output, report);
            say("ColossusCraft snapshot: " + output);
        } catch (IOException e) {
            say("ColossusCraft snapshot failed: " + e.getMessage());
            return 0;
        }
        return 1;
    }

    private static Path writeStarPlan() throws IOException {
        Path output = Minecraft.getInstance().gameDirectory.toPath().resolve("colossuscraft-atm-star-plan.txt");
        Files.writeString(output, buildStarPlanReport());
        return output;
    }

    private static int audit() {
        String report = buildAuditReport();
        Path output = Minecraft.getInstance().gameDirectory.toPath().resolve("colossuscraft-atm10-audit.txt");
        try {
            Files.writeString(output, report);
            say("ColossusCraft audit: " + output);
        } catch (IOException e) {
            say("ColossusCraft audit failed: " + e.getMessage());
            return 0;
        }
        return 1;
    }

    private static int submitNow() {
        int submitted = submitReadyTasks();
        say("ColossusCraft submitted: " + submitted);
        return 1;
    }

    private static int claimNow() {
        claimAllRewards();
        say("ColossusCraft claim all sent");
        return 1;
    }

    private static int stop() {
        enabled = false;
        upstreamPort.stop();
        runBaritone("stop");
        activeTaskId = Long.MIN_VALUE;
        activeMineTarget = "";
        ftbPayloadQueue.clear();
        clearPendingCraft();
        say("ColossusCraft stopped");
        return 1;
    }

    private static int coreOn() {
        upstreamPort.start();
        return 1;
    }

    private static int coreOff() {
        upstreamPort.stop();
        return 1;
    }

    private static int coreStatus() {
        adris.altoclef.tasksystem.Task task = upstreamPort.core().getUserTaskChain().getCurrentTask();
        say("ColossusCraft core port: " + (upstreamPort.running() ? "ON" : "OFF") + " task=" + (task == null ? "none" : task.toString()));
        return 1;
    }

    private static int coreGet(String item, int count) {
        return upstreamPort.runItemTask(item, count) ? 1 : 0;
    }

    private static int coreExec(String command) {
        upstreamPort.executeCommand(command);
        return 1;
    }

    private static int build(String schematic) {
        String trimmed = schematic.trim();
        if (trimmed.isEmpty()) {
            say("ColossusCraft: missing schematic");
            return 0;
        }
        if (trimmed.equalsIgnoreCase("staraltar") || trimmed.equalsIgnoreCase("runic_star_altar")) {
            trimmed = "atm10_runic_star_altar";
        }
        if (runBaritone("build " + trimmed)) {
            say("ColossusCraft build: " + trimmed);
            return 1;
        }
        say("ColossusCraft build failed: put .schematic/.litematic in minecraft/schematics");
        return 0;
    }

    private static int baritone(String command) {
        if (runBaritone(command)) {
            say("Nav: " + command);
            return 1;
        }
        say("Nav command failed");
        return 0;
    }

    private static int altar() {
        say("MI Star Altar min mats: " + starAltarMaterials().entrySet().stream()
                .map(entry -> entry.getValue() + "x " + entry.getKey())
                .collect(Collectors.joining(", ")));
        return 1;
    }

    private static int machines() {
        String report = buildMachineReport();
        Path output = Minecraft.getInstance().gameDirectory.toPath().resolve("colossuscraft-atm10-machines.txt");
        try {
            Files.writeString(output, report);
            say("ATM10 machines: " + output);
        } catch (IOException e) {
            say("ATM10 machines failed: " + e.getMessage());
            return 0;
        }
        return 1;
    }

    private static int mine(String itemId) {
        List<String> targets = mineTargets(itemId);
        if (targets.isEmpty()) {
            say("ColossusCraft: no mine target for " + itemId);
            return 0;
        }
        String command = "mine " + String.join(" ", targets);
        if (runBaritone(command)) {
            say("ColossusCraft mining: " + String.join(", ", targets));
            return 1;
        }
        say("ColossusCraft: nav command failed");
        return 0;
    }

    private static int craft(String itemId, boolean craftAll) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || mc.gameMode == null) {
            say("ColossusCraft craft: no player");
            return 0;
        }
        if (!(player.containerMenu instanceof RecipeBookMenu<?, ?> menu)) {
            say("ColossusCraft craft: open inventory or crafting table");
            return 0;
        }
        ResourceLocation targetId = ResourceLocation.tryParse(itemId.contains(":") ? itemId : "minecraft:" + itemId);
        if (targetId == null) {
            say("ColossusCraft craft: bad item " + itemId);
            return 0;
        }

        RecipeHolder<?> holder = mc.getConnection().getRecipeManager().getRecipes().stream()
                .filter(recipe -> recipe.value() instanceof Recipe<?>)
                .filter(recipe -> {
                    ItemStack result = recipe.value().getResultItem(player.registryAccess());
                    return !result.isEmpty() && targetId.equals(BuiltInRegistries.ITEM.getKey(result.getItem()));
                })
                .findFirst()
                .orElse(null);
        if (holder == null) {
            say("ColossusCraft craft: no recipe for " + targetId);
            return 0;
        }

        mc.getConnection().getConnection().send(new ServerboundPlaceRecipePacket(menu.containerId, holder, craftAll));
        pendingCraftMenuId = menu.containerId;
        pendingCraftResultSlot = menu.getResultSlotIndex();
        pendingCraftDelay = 6;
        say("ColossusCraft craft: " + targetId + (craftAll ? " all" : ""));
        return 1;
    }

    private static int kill(String entityId) {
        String id = AltoClefCompletions.resolveEntityId(entityId);
        if (id == null) {
            say("ColossusCraft kill: unknown entity " + entityId);
            return 0;
        }
        // Drive the core AltoClef kill task: it hunts the nearest matching entity AND fights it
        // (force-field, weapon auto-switch, shield) rather than merely walking to it.
        upstreamPort.executeCommand("kill " + id);
        say("ColossusCraft kill: hunting " + id);
        return 1;
    }

    private static int escape() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            say("escape: not connected");
            return 0;
        }
        return EmergencyHome.goHome("manual escape") ? 1 : 0;
    }

    private static int setEmergencyHome(boolean on) {
        return EmergencyHome.setEnabled(on);
    }

    private static int wardenGolems(int count) {
        upstreamPort.start();
        upstreamPort.core().runUserTask(new WardenGolemTask(count),
                () -> say("Warden golem task finished"));
        say("Warden: spawning " + count + " iron golems");
        return 1;
    }

    private static int wardenStop() {
        upstreamPort.core().getUserTaskChain().cancel(upstreamPort.core());
        upstreamPort.core().stopPathing();
        say("Warden task stopped");
        return 1;
    }

    private static int setAutoHunt(boolean on) {
        upstreamPort.core().getModSettings().setAutoCollectFood(on);
        say("Auto-hunt/gather food: " + (on ? "ON (bot gathers food on its own)" : "OFF (eats only; use /food or /get to gather)"));
        return 1;
    }

    private static int get(String itemId, int count) {
        AltoClefCompletions.ItemMatch match = AltoClefCompletions.resolveItem(itemId);
        if (match == null || match.item() == null) {
            say("ColossusCraft get: unknown item " + itemId);
            return 0;
        }
        String id = match.id() == null ? itemId : match.id().toString();
        // Elytra has no mine/craft/kill recipe (it's in an End-Ship item frame): use the dedicated task.
        if (match.item() == net.minecraft.world.item.Items.ELYTRA) {
            upstreamPort.start();
            upstreamPort.core().runUserTask(new adris.altoclef.tasks.resources.GetElytraTask(),
                    () -> say("ColossusCraft get: elytra task finished"));
            say("ColossusCraft get: hunting an elytra (head to The End)");
            return 1;
        }
        adris.altoclef.tasksystem.Task task;
        if (match.catalogueName() != null && adris.altoclef.TaskCatalogue.taskExists(match.catalogueName())) {
            task = adris.altoclef.TaskCatalogue.getItemTask(match.catalogueName(), count);
        } else if (adris.altoclef.TaskCatalogue.taskExists(match.item())) {
            task = adris.altoclef.TaskCatalogue.getItemTask(match.item(), count);
        } else {
            task = new adris.altoclef.tasks.CollectItemTask(new adris.altoclef.util.ItemTarget(match.item(), count));
        }
        upstreamPort.start();
        upstreamPort.core().runUserTask(task, () -> say("ColossusCraft get: " + id + " task finished"), true);
        say("ColossusCraft get: " + count + "x " + id);
        return 1;
    }

    private static int gotoPos(int x, int y, int z) {
        if (runBaritone("goto " + x + " " + y + " " + z)) {
            say("ColossusCraft goto: " + x + " " + y + " " + z);
            return 1;
        }
        say("ColossusCraft goto: nav command failed");
        return 0;
    }

    private static int gotoPlayer(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            say("ColossusCraft goto: no world");
            return 0;
        }
        for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
            if (p == mc.player) continue;
            if (p.getName().getString().equalsIgnoreCase(name) || p.getGameProfile().getName().equalsIgnoreCase(name)) {
                BlockPos pos = p.blockPosition();
                return gotoPos(pos.getX(), pos.getY(), pos.getZ());
            }
        }
        say("ColossusCraft goto: player not found: " + name);
        return 0;
    }

    private static int gotoEntity(String typeId) {
        String id = AltoClefCompletions.resolveEntityId(typeId);
        if (id == null) id = typeId;
        Entity target = nearestEntity(id, 256.0d);
        if (target == null) {
            say("ColossusCraft goto entity: none visible: " + id);
            return 0;
        }
        BlockPos pos = target.blockPosition();
        return gotoPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static int gotoItem(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            say("ColossusCraft goto item: no world");
            return 0;
        }
        AltoClefCompletions.ItemMatch match = AltoClefCompletions.resolveItem(itemId);
        net.minecraft.world.item.Item item = match != null ? match.item() : null;
        net.minecraft.world.entity.item.ItemEntity nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.item.ItemEntity ie)) continue;
            if (item != null && ie.getItem().getItem() != item) continue;
            double d = e.distanceToSqr(mc.player);
            if (d < bestDist) { bestDist = d; nearest = ie; }
        }
        if (nearest == null) {
            say("ColossusCraft goto item: none visible: " + itemId);
            return 0;
        }
        BlockPos pos = nearest.blockPosition();
        return gotoPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static int follow(String player) {
        upstreamPort.executeCommand("follow " + player);
        say("ColossusCraft follow: " + player);
        return 1;
    }

    private static int food(int units) {
        upstreamPort.executeCommand("food " + units);
        say("ColossusCraft food: collecting " + units + " units");
        return 1;
    }

    private static int come() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            say("ColossusCraft come: no player");
            return 0;
        }
        Entity nearest = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Entity entity : mc.level.players()) {
            if (entity == mc.player) continue;
            double dist = entity.distanceToSqr(mc.player);
            if (dist < bestDist) {
                bestDist = dist;
                nearest = entity;
            }
        }
        if (nearest == null) {
            say("ColossusCraft come: no other player visible");
            return 0;
        }
        BlockPos pos = nearest.blockPosition();
        return gotoPos(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Reports which previously-opened chest(s) hold an item, nearest first, and paths to the closest.
     * NOTE: Minecraft only sends a chest's contents to the client when it is opened, so this can only
     * see containers that you (or the bot) have already opened — it is not an x-ray of unopened chests.
     */
    private static int findChest(String itemId, boolean travel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            say("ColossusCraft findchest: no player");
            return 0;
        }
        AltoClefCompletions.ItemMatch match = AltoClefCompletions.resolveItem(itemId);
        if (match == null || match.item() == null) {
            say("ColossusCraft findchest: unknown item " + itemId);
            return 0;
        }
        ResourceLocation id = match.id();
        net.minecraft.world.item.Item item = match.item();
        adris.altoclef.trackers.storage.ContainerSubTracker tracker = upstreamPort.core().getContainerTracker();
        if (tracker == null) {
            say("ColossusCraft findchest: container tracker not ready");
            return 0;
        }
        net.minecraft.world.phys.Vec3 me = mc.player.position();
        String dim = mc.player.level().dimension().location().toString();
        List<adris.altoclef.trackers.storage.ContainerCache> found = new java.util.ArrayList<>(tracker.getContainersWithItem(item));
        // Only show containers in the dimension we're currently in (positions aren't unique across dims).
        found.removeIf(c -> !c.getDimension().isEmpty() && !c.getDimension().equals(dim));
        found.sort(java.util.Comparator.comparingDouble(c -> c.getBlockPos().getCenter().distanceToSqr(me)));
        if (found.isEmpty()) {
            say("ColossusCraft findchest: no opened chest is known to hold " + id.getPath() + " (open chests to index them)");
            return 0;
        }
        StringBuilder sb = new StringBuilder("ColossusCraft findchest " + id.getPath() + ": ");
        int shown = Math.min(found.size(), 5);
        for (int i = 0; i < shown; i++) {
            adris.altoclef.trackers.storage.ContainerCache c = found.get(i);
            BlockPos p = c.getBlockPos();
            sb.append(c.getItemCount(item)).append("x @ ").append(p.toShortString());
            if (i < shown - 1) sb.append(", ");
        }
        if (found.size() > shown) sb.append(" (+" + (found.size() - shown) + " more)");
        say(sb.toString());
        if (travel) {
            BlockPos p = found.get(0).getBlockPos();
            gotoPos(p.getX(), p.getY(), p.getZ());
        }
        return 1;
    }

    private static boolean emergencyHomeEnabled = true;
    private static int homeCooldown = 0;

    /** Fires ATM10's /home as a last-ditch escape when death is imminent. Always active (safety). */
    private static void emergencyTick() {
        if (!emergencyHomeEnabled) {
            return;
        }
        if (homeCooldown > 0) {
            homeCooldown--;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.getConnection() == null || !p.isAlive()) {
            return;
        }
        String reason = emergencyReason(mc, p);
        if (reason == null) {
            return;
        }
        runBaritone("stop");
        mc.getConnection().sendCommand("home");
        say("EMERGENCY /home: " + reason);
        homeCooldown = 200; // ~10s, avoid spamming while the teleport resolves
    }

    private static String emergencyReason(Minecraft mc, LocalPlayer p) {
        float health = p.getHealth() + p.getAbsorptionAmount();
        if (p.isCreative() || p.isSpectator()) {
            return null;
        }
        if (health <= 6.0f) {
            return "critical health (" + (int) health + ")";
        }
        if (p.isInLava() && !p.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) {
            return "lava";
        }
        if (p.hasEffect(net.minecraft.world.effect.MobEffects.WITHER) && health <= 12.0f) {
            return "wither";
        }
        if (p.isOnFire() && !p.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE) && health <= 10.0f) {
            return "burning";
        }
        if (p.isInWater() && p.getAirSupply() <= 40) {
            return "drowning";
        }
        boolean falling = !p.onGround() && !p.isInWater() && !p.onClimbable() && p.getDeltaMovement().y < -0.5;
        if (falling) {
            int minY = mc.level == null ? -64 : mc.level.getMinBuildHeight();
            if (p.getY() < minY + 16 || (adris.altoclef.util.helpers.WorldHelper.getCurrentDimension() == adris.altoclef.util.Dimension.END && p.getY() < 50)) {
                return "void fall";
            }
            boolean hasWater = hasStoredItem(net.minecraft.world.item.Items.WATER_BUCKET);
            boolean slowFall = p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING);
            float fallDamage = p.fallDistance - 3.0f;
            if (fallDamage >= health - 2.0f && !hasWater && !slowFall) {
                return "lethal fall with no water bucket";
            }
        }
        return null;
    }

    private static boolean hasStoredItem(net.minecraft.world.item.Item item) {
        try {
            return upstreamPort.core().getItemStorage().hasItem(item);
        } catch (Throwable ignored) {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.player.getInventory().contains(new ItemStack(item));
        }
    }

    private static void clientTick(ClientTickEvent.Post event) {
        // ATM10 addon must never interfere with the vanilla AltoClef port: stay fully inert
        // (no FTB packets, no craft-menu/slot manipulation) unless explicitly enabled via /atmquests on.
        if (!enabled) {
            return;
        }
        flushFtbPayloadQueue();
        tickPendingCraft();
        if (++tick % 40 != 0) {
            return;
        }
        if (submitReadyTasks() > 0) {
            claimAllRewards();
            return;
        }
        if (tick % 200 == 0) {
            claimAllRewards();
        }

        QuestTarget target = findNextTarget();
        if (target == null) {
            QuestStep step = findNextQuestStep();
            if (step != null) {
                driveQuestStep(step);
                return;
            }
            if (goalMode == GoalMode.ATM_STAR) {
                driveStarGoal();
            }
            return;
        }
        RouteGate gate = routeGateFor(target.itemId);
        if (gate != null) {
            driveRouteGate(target.taskId, gate);
            return;
        }
        List<String> mineTargets = autoMineTargets(target.itemId);
        if (mineTargets.isEmpty()) {
            if (driveSpecialTarget(target)) {
                return;
            }
            if (goalMode == GoalMode.ATM_STAR && tick % 400 == 0) {
                say("ColossusCraft quest blocker: " + target.itemId + " " + target.progress + "/" + target.required + " quest=" + target.questTitle + " action=" + targetAction(target));
            }
            return;
        }
        runQuestMine(target, mineTargets);
    }

    private static void runQuestMine(QuestTarget target, List<String> mineTargets) {
        String mineTarget = String.join(" ", mineTargets);
        if (target.taskId != activeTaskId || !mineTarget.equals(activeMineTarget) || tick % 1200 == 0) {
            if (runBaritone("mine " + mineTarget)) {
                activeTaskId = target.taskId;
                activeMineTarget = mineTarget;
                say("ColossusCraft quest mine: " + target.itemId + " -> " + mineTarget);
            }
        }
    }

    private static boolean driveSpecialTarget(QuestTarget target) {
        String action = specialActionForItem(target.itemId);
        if (action == null) {
            return false;
        }
        String key = "special:" + target.itemId;
        if (isPiglichItem(target.itemId)) {
            Entity piglich = nearestEntity("allthemodium:piglich", 96.0d);
            if (piglich != null) {
                BlockPos pos = piglich.blockPosition();
                String command = "goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
                if (target.taskId != activeTaskId || !command.equals(activeMineTarget) || tick % 1200 == 0) {
                    if (runBaritone(command)) {
                        activeTaskId = target.taskId;
                        activeMineTarget = command;
                        say("ColossusCraft quest kill: allthemodium:piglich at " + pos.toShortString());
                    }
                }
                return true;
            }
        }
        if (target.taskId != activeTaskId || !key.equals(activeMineTarget) || tick % 1200 == 0) {
            runBaritone("stop");
            activeTaskId = target.taskId;
            activeMineTarget = key;
            say("ColossusCraft quest special: " + action + " quest=" + target.questTitle);
        }
        return true;
    }

    private static void driveRouteGate(long taskId, RouteGate gate) {
        String key = "route:" + gate.action;
        if (taskId != activeTaskId || !key.equals(activeMineTarget) || tick % 1200 == 0) {
            runBaritone("stop");
            activeTaskId = taskId;
            activeMineTarget = key;
            say("ColossusCraft quest route: " + gate.action);
        }
    }

    private static void driveQuestStep(QuestStep step) {
        String key = "step:" + step.action;
        if (step.taskId != activeTaskId || !key.equals(activeMineTarget) || tick % 1200 == 0) {
            runBaritone("stop");
            activeTaskId = step.taskId;
            activeMineTarget = key;
            say("ColossusCraft quest step: " + step.action + " quest=" + step.questTitle);
        }
    }

    private static void driveStarGoal() {
        StarBlocker blocker = findStarBlocker();
        if (blocker == null || blocker.machineStep()) {
            return;
        }
        if (blocker.action().startsWith("route ") || blocker.action().equals("quest/reward gate") || blocker.action().equals("external craft/machine")) {
            if (tick % 1200 == 0) {
                runBaritone("stop");
                say("ColossusCraft star blocker: " + describeBlocker(blocker));
            }
            return;
        }
        List<String> mineTargets = autoMineTargets(blocker.itemId());
        if (mineTargets.isEmpty()) {
            if (tick % 1200 == 0) {
                say("ColossusCraft star blocker: " + describeBlocker(blocker));
            }
            return;
        }
        String mineTarget = String.join(" ", mineTargets);
        if (activeTaskId != Long.MIN_VALUE + 1 || !mineTarget.equals(activeMineTarget) || tick % 1200 == 0) {
            if (runBaritone("mine " + mineTarget)) {
                activeTaskId = Long.MIN_VALUE + 1;
                activeMineTarget = mineTarget;
                say("ColossusCraft star mine: " + displayItem(blocker.itemId()) + " -> " + mineTarget);
            }
        }
    }

    private static int submitReadyTasks() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientQuestFile file = questFile();
        TeamData data = teamData(file);
        if (player == null || mc.getConnection() == null || file == null || data == null) {
            return 0;
        }

        int submitted = 0;
        for (Task task : sortedTasks(file)) {
            if (!isGoalTask(task) || !isTaskReady(data, task)) {
                continue;
            }
            if (task instanceof ItemTask itemTask) {
                if (countMatching(player, itemTask) <= 0) {
                    continue;
                }
            } else if (!task.getClass().getName().endsWith(".CheckmarkTask")) {
                continue;
            }
            sendFtbPayload(new SubmitTaskMessage(task.id));
            submitted++;
            if (submitted >= 8) {
                break;
            }
        }
        return submitted;
    }

    private static void claimAllRewards() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            sendFtbPayload(ClaimAllRewardsMessage.INSTANCE);
        }
    }

    private static void sendFtbPayload(CustomPacketPayload payload) {
        if (ftbPayloadQueue.size() < 64) {
            ftbPayloadQueue.add(payload);
        }
    }

    private static void flushFtbPayloadQueue() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            ftbPayloadQueue.clear();
            ftbPayloadCooldown = 0;
            return;
        }
        if (ftbPayloadCooldown > 0) {
            ftbPayloadCooldown--;
            return;
        }
        CustomPacketPayload payload = ftbPayloadQueue.poll();
        if (payload == null) {
            return;
        }
        try {
            NetworkManager.sendToServer(payload);
            ftbPayloadCooldown = 3;
        } catch (Throwable e) {
            say("FTB packet failed: " + e.getClass().getSimpleName());
        }
    }

    private static void tickPendingCraft() {
        if (pendingCraftResultSlot < 0) {
            return;
        }
        if (pendingCraftDelay > 0) {
            pendingCraftDelay--;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || player.containerMenu.containerId != pendingCraftMenuId) {
            clearPendingCraft();
            return;
        }
        mc.gameMode.handleInventoryMouseClick(pendingCraftMenuId, pendingCraftResultSlot, 0, ClickType.QUICK_MOVE, player);
        clearPendingCraft();
    }

    private static void clearPendingCraft() {
        pendingCraftMenuId = -1;
        pendingCraftResultSlot = -1;
        pendingCraftDelay = 0;
    }

    private static QuestTarget findNextTarget() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientQuestFile file = questFile();
        TeamData data = teamData(file);
        if (player == null || file == null || data == null) {
            return null;
        }

        return sortedTasks(file).stream()
                .filter(AltoClefQuestBot::isGoalTask)
                .filter(task -> task instanceof ItemTask)
                .filter(task -> isTaskReady(data, task))
                .map(task -> (ItemTask) task)
                .sorted(Comparator
                        .comparingInt((ItemTask task) -> questTargetRank(player, data, task))
                        .thenComparingDouble(task -> task.getQuest().getX())
                        .thenComparingDouble(task -> task.getQuest().getY())
                        .thenComparingLong(task -> task.id))
                .map(task -> toTarget(player, data, (ItemTask) task))
                .filter(target -> target != null && target.required > target.progress)
                .findFirst()
                .orElse(null);
    }

    private static QuestStep findNextQuestStep() {
        ClientQuestFile file = questFile();
        TeamData data = teamData(file);
        if (file == null || data == null) {
            return null;
        }

        return sortedTasks(file).stream()
                .filter(AltoClefQuestBot::isGoalTask)
                .filter(task -> !(task instanceof ItemTask))
                .filter(task -> isTaskReady(data, task))
                .map(AltoClefQuestBot::toQuestStep)
                .filter(step -> step != null)
                .min(Comparator.comparingInt(AltoClefQuestBot::questStepRank)
                        .thenComparingDouble(step -> step.questX)
                        .thenComparingDouble(step -> step.questY)
                        .thenComparingLong(step -> step.taskId))
                .orElse(null);
    }

    private static QuestStep toQuestStep(Task task) {
        String type = taskType(task);
        String chapter = task.getQuestChapter().getFilename().toLowerCase();
        String questTitle = task.getQuest().getTitle().getString();
        String lowerTitle = questTitle.toLowerCase();
        String action;

        if (type.contains("structure")) {
            if (chapter.contains("eternal_starlight") || lowerTitle.contains("gatekeeper")) {
                action = "find eternal_starlight:portal_ruins, defeat Gatekeeper, activate portal with Orb of Prophecy";
            } else {
                action = "find required structure";
            }
        } else if (type.contains("dimension")) {
            if (chapter.contains("eternal_starlight") || lowerTitle.contains("starlight")) {
                action = "enter eternal_starlight:starlight via activated portal";
            } else {
                action = "enter required dimension";
            }
        } else if (type.contains("biome")) {
            action = "travel to required biome";
        } else if (type.contains("kill") || type.contains("entity")) {
            action = "kill quest target";
        } else if (type.contains("checkmark")) {
            action = "submit checkmark";
        } else if (type.contains("location")) {
            action = "reach quest location";
        } else {
            action = "complete " + type;
        }

        return new QuestStep(task.id, questTitle, type, action, task.getQuest().getX(), task.getQuest().getY());
    }

    private static int questStepRank(QuestStep step) {
        if (step.type.contains("structure")) {
            return 0;
        }
        if (step.type.contains("dimension")) {
            return 1;
        }
        if (step.type.contains("biome") || step.type.contains("location")) {
            return 2;
        }
        if (step.type.contains("kill") || step.type.contains("entity")) {
            return 3;
        }
        if (step.type.contains("checkmark")) {
            return 4;
        }
        return 5;
    }

    private static QuestTarget toTarget(LocalPlayer player, TeamData data, ItemTask task) {
        ItemStack stack = task.getItemStack();
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        long progress = Math.min(task.getMaxProgress(), data.getProgress(task) + countMatching(player, task));
        return new QuestTarget(task.id, itemKey.toString(), task.getQuest().getTitle().getString(), task.getMaxProgress(), progress);
    }

    private static int questTargetRank(LocalPlayer player, TeamData data, ItemTask task) {
        QuestTarget target = toTarget(player, data, task);
        if (target == null || target.required <= target.progress) {
            return 900_000;
        }
        if (countMatching(player, task) > 0) {
            return -10_000;
        }

        int rank = itemStageRank(target.itemId);
        RouteGate gate = routeGateFor(target.itemId);
        if (gate != null) {
            rank += 250;
        }
        if (!autoMineTargets(target.itemId).isEmpty()) {
            rank -= 100;
        }
        return rank;
    }

    private static int itemStageRank(String itemId) {
        String id = baseItemId(itemId);
        if (id.equals("minecraft:netherite_ingot") || id.equals("allthemodium:teleport_pad")) {
            return 0;
        }
        if (id.startsWith("allthemodium:allthemodium") || id.equals("allthemodium:ancient_stone")) {
            return 1_000;
        }
        if (id.startsWith("allthemodium:vibranium")) {
            return 2_000;
        }
        if (id.startsWith("allthemodium:unobtainium")) {
            return 3_000;
        }
        if (id.equals("allthemodium:piglich_heart") || id.equals("allthemodium:piglich_heart_block")) {
            return 4_000;
        }
        if (id.startsWith("modern_industrialization:runic_") || id.equals("modern_industrialization:auto_forge")
                || id.equals("modern_industrialization:star_altar")) {
            return 5_000;
        }
        if (id.equals(ATM_STAR) || id.equals("allthetweaks:atm_star_shard") || id.startsWith("kubejs:atm_star_shard_")) {
            return 8_000;
        }
        if (STAR_RECIPES.containsKey(itemId) || STAR_RECIPES.containsKey(id)) {
            return 6_000;
        }
        if (!autoMineTargets(id).isEmpty()) {
            return 1_500;
        }
        return 7_000;
    }

    private static String targetAction(QuestTarget target) {
        RouteGate gate = routeGateFor(target.itemId);
        if (gate != null) {
            return gate.action;
        }
        String special = specialActionForItem(target.itemId);
        if (special != null) {
            return special;
        }
        List<String> mines = autoMineTargets(target.itemId);
        return mines.isEmpty() ? "craft/machine/gather" : "mine " + String.join(" ", mines);
    }

    private static String specialActionForItem(String itemId) {
        if (isPiglichItem(itemId)) {
            return "kill allthemodium:piglich in allthemodium:the_other ancient_pyramid or use HNN/EnderIO farm";
        }
        return null;
    }

    private static boolean isPiglichItem(String itemId) {
        String id = baseItemId(itemId);
        return id.equals("allthemodium:piglich_heart") || id.equals("allthemodium:piglich_heart_block");
    }

    private static RouteGate routeGateFor(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return null;
        }
        List<String> requiredDimensions = requiredDimensionsFor(itemId);
        String current = player.level().dimension().location().toString();
        if (requiredDimensions.isEmpty() || requiredDimensions.contains(current)) {
            return null;
        }
        if (requiredDimensions.contains("eternal_starlight:starlight")) {
            return new RouteGate("eternal_starlight:starlight", "enter eternal_starlight:starlight first: find portal_ruins, defeat Gatekeeper, activate portal with Orb of Prophecy");
        }
        if (requiredDimensions.contains("allthemodium:the_other")) {
            return new RouteGate("allthemodium:the_other", "enter allthemodium:the_other first: use teleport pad in Nether");
        }
        if (requiredDimensions.contains("allthemodium:mining")) {
            return new RouteGate("allthemodium:mining", "enter allthemodium:mining first: use teleport pad in Overworld");
        }
        if (requiredDimensions.size() == 1) {
            return new RouteGate(requiredDimensions.get(0), "enter " + requiredDimensions.get(0) + " first");
        }
        return new RouteGate(requiredDimensions.get(0), "enter one of " + String.join(" or ", requiredDimensions) + " first");
    }

    private static List<String> requiredDimensionsFor(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(baseItemId(itemId));
        if (id == null) {
            return List.of();
        }
        if (id.getNamespace().equals("eternal_starlight")) {
            return List.of("eternal_starlight:starlight");
        }
        if (id.getNamespace().equals("allthemodium")) {
            String path = id.getPath();
            if (isAllthemodiumOreFamily(path, "vibranium")) {
                return List.of("minecraft:the_nether", "allthemodium:the_other");
            }
            if (isAllthemodiumOreFamily(path, "unobtainium")) {
                return List.of("minecraft:the_end");
            }
            if (isAllthemodiumOreFamily(path, "allthemodium")) {
                return List.of("minecraft:overworld", "allthemodium:mining");
            }
            if (path.startsWith("piglich_heart") || path.equals("soul_lava_bucket") || path.equals("soul_lava")) {
                return List.of("allthemodium:the_other");
            }
        }
        return List.of();
    }

    private static boolean isAllthemodiumOreFamily(String path, String metal) {
        return path.equals(metal + "_ingot")
                || path.equals("raw_" + metal)
                || path.equals(metal + "_block")
                || path.equals(metal + "_ore")
                || path.equals(metal + "_slate_ore")
                || path.equals("deepslate_" + metal + "_ore")
                || path.equals("other_" + metal + "_ore");
    }

    private static boolean isTaskReady(TeamData data, Task task) {
        Quest quest = task.getQuest();
        return !data.isCompleted(task)
                && quest.isVisible(data)
                && data.canStartTasks(quest)
                && !data.isCompleted(quest);
    }

    private static boolean isGoalTask(Task task) {
        if (goalMode == GoalMode.ALL_QUESTS) {
            return true;
        }
        if (isStarQuest(task.getQuest())) {
            return true;
        }
        String chapter = task.getQuestChapter().getFilename().toLowerCase();
        return chapter.contains("star") || chapter.contains("allthemodium");
    }

    private static boolean isStarQuest(Quest quest) {
        String title = quest.getTitle().getString().toLowerCase();
        if (title.contains("atm star") || title.contains("star shard") || title.contains("runic")) {
            return true;
        }
        for (Task task : quest.getTasks()) {
            if (task instanceof ItemTask itemTask) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(itemTask.getItemStack().getItem());
                if (isStarItem(key.toString())) {
                    return true;
                }
            }
        }
        for (var reward : quest.getRewards()) {
            if (reward instanceof ItemReward itemReward) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(itemReward.getItem().getItem());
                if (isStarItem(key.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isStarItem(String itemId) {
        if (STAR_RECIPES.containsKey(itemId)) {
            return true;
        }
        return itemId.equals(ATM_STAR)
                || itemId.equals("allthetweaks:atm_star_shard")
                || itemId.equals("allthetweaks:atm_star_block")
                || itemId.startsWith("kubejs:atm_star_shard_")
                || itemId.equals("modern_industrialization:star_altar")
                || itemId.startsWith("modern_industrialization:runic_")
                || itemId.equals("modern_industrialization:auto_forge");
    }

    private static String buildAuditReport() {
        ClientQuestFile file = questFile();
        TeamData data = teamData(file);
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (file == null || data == null || player == null) {
            return "ColossusCraft ATM10 audit\nFTB Quests not synced.\n";
        }

        Map<String, Integer> total = new LinkedHashMap<>();
        Map<String, Integer> ready = new LinkedHashMap<>();
        Map<String, Integer> completed = new LinkedHashMap<>();
        ArrayList<QuestTarget> missingStar = new ArrayList<>();
        int mineable = 0;
        int machine = 0;
        int autoSubmit = 0;

        for (Task task : sortedTasks(file)) {
            String type = taskType(task);
            increment(total, type);
            if (data.isCompleted(task)) {
                increment(completed, type);
                continue;
            }
            if (!isTaskReady(data, task)) {
                continue;
            }
            increment(ready, type);
            if (task instanceof ItemTask itemTask) {
                QuestTarget target = toTarget(player, data, itemTask);
                if (target != null && target.required > target.progress) {
                    String action = targetAction(target);
                    if (isStarTask(task)) {
                        missingStar.add(target);
                    }
                    if (action.startsWith("mine ")) {
                        mineable++;
                    } else {
                        machine++;
                    }
                }
            } else if (task.getClass().getName().endsWith(".CheckmarkTask")) {
                autoSubmit++;
            }
        }

        missingStar.sort(Comparator.comparingLong(target -> target.required - target.progress));
        StringBuilder report = new StringBuilder();
        report.append("ColossusCraft ATM10 audit\n");
        report.append("goal=").append(goalMode.label).append('\n');
        report.append("source=FTB Quests synced client file\n\n");
        report.append("task totals=").append(total).append('\n');
        report.append("completed=").append(completed).append('\n');
        report.append("ready=").append(ready).append("\n\n");
        report.append("automation coverage\n");
        report.append("mineable item tasks=").append(mineable).append('\n');
        report.append("craft/machine item tasks=").append(machine).append('\n');
        report.append("auto-submit checkmarks=").append(autoSubmit).append('\n');
        report.append("kill tasks=ColossusCraft Guard can fight nearby, travel/spawn setup still needed\n");
        report.append("dimension/biome/structure/location=detected by FTB Quests, route/setup still needed\n");
        report.append("machinery/reactors=covered by ATM Star plan gates; machine-specific IO still required\n\n");
        StarBlocker blocker = findStarBlocker();
        report.append("ATM Star live blocker=").append(blocker == null ? "none in carried inventory" : describeBlocker(blocker)).append("\n");
        report.append("full star plan=colossuscraft-atm-star-plan.txt via /atmquests starplan\n\n");
        report.append("ATM Star next blockers\n");
        for (QuestTarget target : missingStar.stream().limit(30).toList()) {
            report.append("- ").append(target.itemId)
                    .append(" ").append(target.progress).append('/').append(target.required)
                    .append(" quest=").append(target.questTitle)
                    .append(" action=").append(targetAction(target))
                    .append('\n');
        }
        report.append("\nMI Runic Star Altar minimum materials\n");
        starAltarMaterials().forEach((item, count) -> report.append("- ").append(count).append("x ").append(item).append('\n'));
        return report.toString();
    }

    private static String buildSnapshotReport() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientQuestFile file = questFile();
        TeamData data = teamData(file);
        StringBuilder report = new StringBuilder();
        report.append("ColossusCraft snapshot\n");
        if (player == null) {
            report.append("no player\n");
            return report.toString();
        }

        report.append("dimension=").append(player.level().dimension().location()).append('\n');
        report.append("pos=").append(player.blockPosition().toShortString()).append('\n');
        QuestTarget target = findNextTarget();
        report.append("quest_target=").append(target == null ? "none" : target.itemId + " " + target.progress + "/" + target.required + " quest=" + target.questTitle).append('\n');
        if (target == null) {
            QuestStep step = findNextQuestStep();
            report.append("quest_action=").append(step == null ? "none" : step.action + " quest=" + step.questTitle).append('\n');
        } else {
            report.append("quest_action=").append(targetAction(target)).append('\n');
        }
        StarBlocker blocker = findStarBlocker();
        report.append("recipe_fallback=").append(blocker == null ? "none" : describeBlocker(blocker)).append('\n');
        if (file == null || data == null) {
            report.append("ftb_quests=not synced\n");
        }

        report.append("\ninventory key items\n");
        for (String item : keySnapshotItems()) {
            long count = countInventory(player, item);
            if (count > 0) {
                report.append("- ").append(item).append("=").append(count).append('\n');
            }
        }

        appendOpenContainerSnapshot(report, player);

        report.append("\nnearby entities r=32\n");
        Map<String, Integer> entities = nearbyEntities(player, 32.0d);
        entities.entrySet().stream().limit(60).forEach(entry -> report.append("- ").append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));

        report.append("\nnearby useful blocks r=12\n");
        Map<String, Integer> blocks = nearbyUsefulBlocks(player, 12);
        blocks.entrySet().stream().limit(80).forEach(entry -> report.append("- ").append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));
        return report.toString();
    }

    private static void appendOpenContainerSnapshot(StringBuilder report, LocalPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu) {
            report.append("\nopen container\nnone\n");
            return;
        }

        Map<ItemStack, Boolean> seen = new IdentityHashMap<>();
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || seen.containsKey(stack)) {
                continue;
            }
            seen.put(stack, Boolean.TRUE);
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            counts.put(key.toString(), counts.getOrDefault(key.toString(), 0) + stack.getCount());
        }

        report.append("\nopen container/ender chest visible items\n");
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(80)
                .forEach(entry -> report.append("- ").append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));
    }

    private static List<String> keySnapshotItems() {
        return List.of(
                "allthemodium:piglich_heart",
                "allthemodium:piglich_heart_block",
                "allthemodium:allthemodium_ingot",
                "allthemodium:vibranium_ingot",
                "allthemodium:unobtainium_ingot",
                "allthetweaks:atm_star_shard",
                ATM_STAR,
                "modern_industrialization:star_altar",
                "modern_industrialization:auto_forge",
                "modern_industrialization:runic_crucible",
                "modern_industrialization:runic_enchanter",
                "modern_industrialization:turbo_upgrade",
                "modern_industrialization:quantum_upgrade",
                "forbidden_arcanus:corrupted_arcane_crystal"
        );
    }

    private static Map<String, Integer> nearbyEntities(LocalPlayer player, double radius) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        AABB box = player.getBoundingBox().inflate(radius);
        for (Entity entity : player.level().getEntities(player, box)) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            increment(counts, key.toString());
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static Entity nearestEntity(String entityId, double radius) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return null;
        }
        AABB box = player.getBoundingBox().inflate(radius);
        return player.level().getEntities(player, box).stream()
                .filter(entity -> entityId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()))
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
                .orElse(null);
    }

    private static Map<String, Integer> nearbyUsefulBlocks(LocalPlayer player, int radius) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        BlockPos center = player.blockPosition();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = player.level().getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    String id = key.toString();
                    if (isUsefulNearbyBlock(id)) {
                        increment(counts, id);
                    }
                }
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static boolean isUsefulNearbyBlock(String id) {
        return id.contains("ore")
                || id.contains("chest")
                || id.contains("barrel")
                || id.contains("spawner")
                || id.contains("hatch")
                || id.contains("altar")
                || id.contains("forge")
                || id.contains("crucible")
                || id.contains("enchanter")
                || id.contains("controller")
                || id.contains("crafter")
                || id.contains("furnace")
                || id.contains("generator")
                || id.contains("cable")
                || id.contains("pipe");
    }

    private static String buildStarPlanReport() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        StringBuilder report = new StringBuilder();
        report.append("ColossusCraft ATM10 Star plan\n");
        report.append("source=local ATM10 KubeJS + FTB Quests client state\n");
        report.append("final_recipe=kubejs/server_scripts/modpack/runic_multis/recipes/star_altar.js\n");
        report.append("component_recipes=kubejs/server_scripts/modpack/att_items.js, atm_alloys.js, mini_portals.js\n\n");

        QuestTarget target = findNextTarget();
        if (target == null) {
            QuestStep step = findNextQuestStep();
            report.append("live quest=").append(step == null ? "none" : step.action + " quest=" + step.questTitle).append('\n');
        } else {
            report.append("live quest=").append(target.itemId).append(' ')
                    .append(target.progress).append('/').append(target.required)
                    .append(" quest=").append(target.questTitle)
                    .append(" action=").append(targetAction(target)).append('\n');
        }
        StarBlocker blocker = findStarBlocker();
        report.append("live blocker=").append(blocker == null ? "none in carried inventory" : describeBlocker(blocker)).append("\n\n");

        StarRecipe finalRecipe = recipeFor(ATM_STAR, player);
        report.append("selected ATM Star route=").append(finalRecipe.outputLabel())
                .append(" via ").append(finalRecipe.machine()).append('\n');
        report.append("route_score=").append(recipeScore(finalRecipe, player))
                .append(" lower is easier from carried inventory\n\n");
        appendRouteComparison(report, player);

        report.append("\nfinal ATM Star inputs\n");
        for (Need need : finalRecipe.inputs()) {
            appendNeedLine(report, player, need, 1);
        }

        appendLeafAudit(report, player);
        appendRunicPowerAudit(report, player);

        report.append("\nrecursive recipe manifest\n");
        for (StarRecipe recipe : STAR_RECIPES.values()) {
            report.append(recipe.outputCount()).append("x ").append(displayItem(recipe.outputId()))
                    .append(" <= ").append(recipe.machine()).append('\n');
            if (!recipe.note().isEmpty()) {
                report.append("  note=").append(recipe.note()).append('\n');
            }
            for (Need need : recipe.inputs()) {
                report.append("  - ").append(need.count()).append("x ").append(displayNeed(need));
                if (!need.note().isEmpty()) {
                    report.append(" (").append(need.note()).append(')');
                }
                report.append('\n');
            }
        }

        report.append("\n").append(buildMachineReport());
        report.append("\nauto driver\n");
        report.append("- submits/claims FTB item and checkmark tasks\n");
        report.append("- mines safe natural ore/block blockers through Baritone\n");
        report.append("- reports craft/machine/blocker when recipe leaf is not mineable\n");
        report.append("- Star shards are quest/reward gated: no normal allthetweaks:atm_star_shard recipe found locally\n");
        return report.toString();
    }

    private static void appendRouteComparison(StringBuilder report, LocalPlayer player) {
        report.append("ATM Star route options\n");
        for (StarRecipe route : atmStarRoutes()) {
            LinkedHashMap<String, LeafNeed> leaves = new LinkedHashMap<>();
            collectRecipeLeaves(route, 1, leaves, new HashSet<>(), player);
            long missing = leaves.values().stream().filter(leaf -> countNeedSafe(player, leaf.need()) < leaf.required()).count();
            long mineable = leaves.values().stream().filter(leaf -> countNeedSafe(player, leaf.need()) < leaf.required())
                    .filter(leaf -> leafAction(leaf.need(), player).startsWith("mine ")).count();
            long routed = leaves.values().stream().filter(leaf -> countNeedSafe(player, leaf.need()) < leaf.required())
                    .filter(leaf -> leafAction(leaf.need(), player).startsWith("route ")).count();
            long gated = leaves.values().stream().filter(leaf -> countNeedSafe(player, leaf.need()) < leaf.required())
                    .filter(leaf -> leafAction(leaf.need(), player).equals("quest/reward gate")).count();
            report.append("- ").append(route.outputLabel())
                    .append(" score=").append(recipeScore(route, player))
                    .append(" missing=").append(missing)
                    .append(" mine=").append(mineable)
                    .append(" route=").append(routed)
                    .append(" quest=").append(gated)
                    .append(" machine=").append(Math.max(0L, missing - mineable - routed - gated))
                    .append(" note=").append(route.note())
                    .append('\n');
        }
    }

    private static void appendRunicPowerAudit(StringBuilder report, LocalPlayer player) {
        long quantum = player == null ? 0 : countInventory(player, "modern_industrialization:quantum_upgrade");
        long corrupted = player == null ? 0 : countInventory(player, "forbidden_arcanus:corrupted_arcane_crystal");
        long highlyAdvanced = player == null ? 0 : countInventory(player, "modern_industrialization:highly_advanced_upgrade");
        long turbo = player == null ? 0 : countInventory(player, "modern_industrialization:turbo_upgrade");
        long advanced = player == null ? 0 : countInventory(player, "modern_industrialization:advanced_upgrade");
        long capacity = quantum > 0 ? 999999999L : corrupted * 512L + highlyAdvanced * 512L + turbo * 64L + advanced * 16L;
        long needed = Math.max(0L, 2048L - capacity);

        report.append("\nrunic power gate\n");
        report.append("source=kubejs/data/modern_industrialization/data_maps/item/machine_upgrades.json\n");
        report.append("star recipes require 2048 EU/t; upgrades found capacity=").append(capacity).append(" missing=").append(needed).append('\n');
        report.append("- quantum_upgrade=").append(quantum).append(" each=999999999\n");
        report.append("- corrupted_arcane_crystal=").append(corrupted).append(" each=512\n");
        report.append("- highly_advanced_upgrade=").append(highlyAdvanced).append(" each=512\n");
        report.append("- turbo_upgrade=").append(turbo).append(" each=64\n");
        report.append("- advanced_upgrade=").append(advanced).append(" each=16\n");
        if (needed > 0) {
            report.append("action=add 1 quantum OR ")
                    .append(divideUp(needed, 512)).append(" corrupted/highly_advanced OR ")
                    .append(divideUp(needed, 64)).append(" turbo upgrades before Star Altar runs\n");
        } else {
            report.append("action=upgrade capacity ok for Star Altar\n");
        }
    }

    private static void appendLeafAudit(StringBuilder report, LocalPlayer player) {
        LinkedHashMap<String, LeafNeed> leaves = new LinkedHashMap<>();
        collectLeaves(ATM_STAR, 1, leaves, new HashSet<>(), player);

        ArrayList<LeafNeed> missing = new ArrayList<>();
        for (LeafNeed leaf : leaves.values()) {
            long have = player == null ? 0 : countNeed(player, leaf.need());
            if (have < leaf.required()) {
                missing.add(leaf);
            }
        }
        missing.sort(Comparator.comparingInt((LeafNeed leaf) -> leafBlockerRank(leaf.need(), player))
                .thenComparing(leaf -> displayNeed(leaf.need())));

        long mineable = missing.stream().filter(leaf -> leafAction(leaf.need(), player).startsWith("mine ")).count();
        long routed = missing.stream().filter(leaf -> leafAction(leaf.need(), player).startsWith("route ")).count();
        long quest = missing.stream().filter(leaf -> leafAction(leaf.need(), player).equals("quest/reward gate")).count();
        long external = missing.size() - mineable - routed - quest;

        report.append("\nblocker audit\n");
        report.append("missing leaves=").append(missing.size())
                .append(" mineable=").append(mineable)
                .append(" routed=").append(routed)
                .append(" quest_gated=").append(quest)
                .append(" external_craft_machine=").append(external).append('\n');
        report.append("meaning: no hidden dead end; every missing leaf is classified before run\n");
        for (LeafNeed leaf : missing.stream().limit(120).toList()) {
            long have = player == null ? 0 : countNeed(player, leaf.need());
            report.append("- ").append(displayNeed(leaf.need()))
                    .append(" ").append(have).append('/').append(leaf.required())
                    .append(" action=").append(leafAction(leaf.need(), player));
            if (!leaf.need().note().isEmpty()) {
                report.append(" note=").append(leaf.need().note());
            }
            report.append('\n');
        }
    }

    private static void collectLeaves(String itemId, long required, Map<String, LeafNeed> leaves, Set<String> visiting, LocalPlayer player) {
        StarRecipe recipe = recipeFor(itemId, player);
        if (recipe == null || visiting.contains(itemId)) {
            Need leaf = need(required, itemId);
            addLeaf(leaves, leaf, required);
            return;
        }

        collectRecipeLeaves(recipe, required, leaves, visiting, player);
    }

    private static void collectRecipeLeaves(StarRecipe recipe, long required, Map<String, LeafNeed> leaves, Set<String> visiting, LocalPlayer player) {
        String itemId = recipe.outputId();
        if (visiting.contains(itemId)) {
            Need leaf = need(required, itemId);
            addLeaf(leaves, leaf, required);
            return;
        }
        visiting.add(itemId);
        long batches = Math.max(1L, divideUp(required, recipe.outputCount()));
        for (Need need : recipe.inputs()) {
            collectNeedLeaves(need, need.count() * batches, leaves, visiting, player);
        }
        visiting.remove(itemId);
    }

    private static void collectNeedLeaves(Need need, long required, Map<String, LeafNeed> leaves, Set<String> visiting, LocalPlayer player) {
        String recipeOption = bestRecipeOption(need.itemIds(), player);
        if (recipeOption != null) {
            collectLeaves(recipeOption, required, leaves, visiting, player);
            return;
        }
        addLeaf(leaves, need.withCount(required), required);
    }

    private static void addLeaf(Map<String, LeafNeed> leaves, Need need, long required) {
        String key = String.join("|", need.itemIds()) + "|" + need.label() + "|" + need.note();
        LeafNeed existing = leaves.get(key);
        leaves.put(key, new LeafNeed(need, required + (existing == null ? 0 : existing.required())));
    }

    private static String leafAction(Need need) {
        return leafAction(need, Minecraft.getInstance().player);
    }

    private static String leafAction(Need need, LocalPlayer player) {
        if (need.note().toLowerCase().contains("quest") || need.note().toLowerCase().contains("data model")) {
            return "quest/reward gate";
        }
        RouteGate gate = routeGateForNeed(need, player);
        if (gate != null) {
            return "route " + gate.action();
        }
        List<String> mines = need.itemIds().stream()
                .flatMap(item -> autoMineTargets(item).stream())
                .distinct()
                .toList();
        if (!mines.isEmpty()) {
            return "mine " + String.join(" ", mines);
        }
        return "external craft/machine";
    }

    private static RouteGate routeGateForNeed(Need need, LocalPlayer player) {
        if (player == null) {
            return null;
        }
        for (String item : need.itemIds()) {
            RouteGate gate = routeGateFor(item);
            if (gate != null) {
                return gate;
            }
        }
        return null;
    }

    private static int leafBlockerRank(Need need, LocalPlayer player) {
        String action = leafAction(need, player);
        if (action.startsWith("mine ")) {
            return 0;
        }
        if (action.startsWith("route ")) {
            return 10;
        }
        if (action.equals("quest/reward gate")) {
            return 50;
        }
        return 100;
    }

    private static StarRecipe recipeFor(String itemId, LocalPlayer player) {
        if (ATM_STAR.equals(itemId)) {
            return atmStarRoutes().stream()
                    .min(Comparator.comparingLong(route -> recipeScore(route, player)))
                    .orElse(STAR_RECIPES.get(ATM_STAR));
        }
        return STAR_RECIPES.get(itemId);
    }

    private static String bestRecipeOption(List<String> itemIds, LocalPlayer player) {
        return itemIds.stream()
                .filter(item -> recipeFor(item, player) != null)
                .min(Comparator.comparingLong(item -> recipeScore(recipeFor(item, player), player)))
                .orElse(null);
    }

    private static long recipeScore(StarRecipe recipe, LocalPlayer player) {
        if (recipe == null) {
            return Long.MAX_VALUE / 4;
        }
        LinkedHashMap<String, LeafNeed> leaves = new LinkedHashMap<>();
        collectRecipeLeaves(recipe, 1, leaves, new HashSet<>(), player);
        long score = 0;
        for (LeafNeed leaf : leaves.values()) {
            long have = player == null ? 0 : countNeed(player, leaf.need());
            long missing = Math.max(0L, leaf.required() - have);
            if (missing <= 0) {
                continue;
            }
            String action = leafAction(leaf.need(), player);
            long weight = 20_000L;
            if (action.startsWith("mine ")) {
                weight = 10L;
            } else if (action.startsWith("route ")) {
                weight = 200L;
            } else if (action.equals("quest/reward gate")) {
                weight = 5_000L;
            }
            score += weight + Math.min(missing, 10_000L);
        }
        return score;
    }

    private static List<StarRecipe> atmStarRoutes() {
        StarRecipe direct = STAR_RECIPES.get(ATM_STAR);
        StarRecipe fragment = recipe(ATM_STAR, 1, "ATM Star (fragment route)", "MI Runic Star Altar 2048 EU/t 200t", "alternate local star_altar.js recipe",
                need(1, "actuallyadditions:lens_of_the_killer"),
                need(1, "kubejs:atm_star_shard_1"),
                need(1, "kubejs:atm_star_shard_2"),
                need(1, "kubejs:atm_star_shard_3"),
                need(1, "kubejs:atm_star_shard_4"),
                need(1, "kubejs:atm_star_shard_5"),
                need(1, "forbidden_arcanus:mundabitur_dust"),
                need(1, "forbidden_arcanus:corrupti_dust"),
                needNamed(1, INFUSED_PATRICK_STAR, "Infused Patrick Star", "NBT custom name + Mending I"));
        return direct == null ? List.of(fragment) : List.of(direct, fragment);
    }

    private static void appendNeedLine(StringBuilder report, LocalPlayer player, Need need, long scale) {
        long required = need.count() * scale;
        long have = player == null ? 0 : countNeed(player, need);
        report.append("- ").append(required).append("x ").append(displayNeed(need))
                .append(" have=").append(have);
        if (!need.note().isEmpty()) {
            report.append(" note=").append(need.note());
        }
        report.append('\n');
    }

    private static String buildMachineReport() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        StringBuilder report = new StringBuilder();
        report.append("ATM10 MI runic machine minimum structure materials\n");
        report.append("source=kubejs/startup_scripts/Modern-Industrialization/multiblocks\n");
        appendRunicPowerAudit(report, player);
        for (MachinePlan plan : machinePlans()) {
            report.append('\n').append(plan.name()).append('\n');
            report.append(plan.note()).append('\n');
            for (Map.Entry<String, Integer> entry : plan.materials().entrySet()) {
                long have = player == null ? 0 : countInventory(player, entry.getKey());
                report.append("- ").append(entry.getValue()).append("x ").append(entry.getKey())
                        .append(" have=").append(have).append('\n');
            }
        }
        return report.toString();
    }

    private static StarBlocker findStarBlocker() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return null;
        }
        if (countInventory(player, ATM_STAR) >= 1) {
            return null;
        }
        ArrayList<LeafNeed> missing = missingStarLeaves(player);
        if (!missing.isEmpty()) {
            LeafNeed leaf = missing.get(0);
            Need need = leaf.need();
            return new StarBlocker(need.itemId(), displayNeed(need), leaf.required(), countNeed(player, need), leafAction(need, player), false);
        }
        return firstMissing(ATM_STAR, 1, player, new HashSet<>(), 0);
    }

    private static ArrayList<LeafNeed> missingStarLeaves(LocalPlayer player) {
        LinkedHashMap<String, LeafNeed> leaves = new LinkedHashMap<>();
        collectLeaves(ATM_STAR, 1, leaves, new HashSet<>(), player);
        ArrayList<LeafNeed> missing = new ArrayList<>();
        for (LeafNeed leaf : leaves.values()) {
            if (countNeed(player, leaf.need()) < leaf.required()) {
                missing.add(leaf);
            }
        }
        missing.sort(Comparator.comparingInt((LeafNeed leaf) -> leafBlockerRank(leaf.need(), player))
                .thenComparingLong(LeafNeed::required)
                .thenComparing(leaf -> displayNeed(leaf.need())));
        return missing;
    }

    private static StarBlocker firstMissing(String itemId, long required, LocalPlayer player, Set<String> visiting, int depth) {
        long have = countInventory(player, itemId);
        if (have >= required) {
            return null;
        }

        StarRecipe recipe = recipeFor(itemId, player);
        if (recipe == null || visiting.contains(itemId) || depth > 12) {
            return new StarBlocker(itemId, displayItem(itemId), required, have, "gather", false);
        }

        visiting.add(itemId);
        long batches = Math.max(1L, divideUp(required - have, recipe.outputCount()));
        for (Need need : recipe.inputs()) {
            StarBlocker blocker = firstMissingNeed(need, need.count() * batches, player, visiting, depth + 1);
            if (blocker != null) {
                visiting.remove(itemId);
                return blocker;
            }
        }
        visiting.remove(itemId);
        return new StarBlocker(itemId, displayItem(itemId), required, have, "run " + recipe.machine() + " recipe", true);
    }

    private static StarBlocker firstMissingNeed(Need need, long required, LocalPlayer player, Set<String> visiting, int depth) {
        long have = countNeed(player, need);
        if (have >= required) {
            return null;
        }
        for (String option : need.itemIds()) {
            if (recipeFor(option, player) != null) {
                StarBlocker blocker = firstMissing(option, required, player, visiting, depth);
                if (blocker != null) {
                    return blocker;
                }
            }
        }
        return new StarBlocker(need.itemId(), displayNeed(need), required, have, "gather", false);
    }

    private static long divideUp(long value, long divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static String describeBlocker(StarBlocker blocker) {
        List<String> mines = autoMineTargets(blocker.itemId());
        String action = blocker.machineStep() || !blocker.action().equals("gather")
                ? blocker.action()
                : (mines.isEmpty() ? blocker.action() : "mine " + String.join(" ", mines));
        return blocker.label() + " " + blocker.have() + "/" + blocker.required() + " action=" + action;
    }

    private static long countNeed(LocalPlayer player, Need need) {
        long total = 0;
        for (String itemId : need.itemIds()) {
            total += countInventory(player, itemId);
        }
        return total;
    }

    private static long countNeedSafe(LocalPlayer player, Need need) {
        return player == null ? 0 : countNeed(player, need);
    }

    private static long countInventory(LocalPlayer player, String itemId) {
        String baseId = baseItemId(itemId);
        long count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!baseId.equals(key.toString())) {
                continue;
            }
            if (AWAKENED_ALLOY_BLOCK.equals(itemId) && !stack.getHoverName().getString().equals("Awakened Unobtainium-Vibranium Alloy Block")) {
                continue;
            }
            if (INFUSED_PATRICK_STAR.equals(itemId) && !stack.getHoverName().getString().equals("Infused Patrick Star")) {
                continue;
            }
            count += stack.getCount();
        }
        return count;
    }

    private static String baseItemId(String itemId) {
        String value = itemId;
        int hash = value.indexOf('#');
        if (hash >= 0) {
            value = value.substring(0, hash);
        }
        int nbt = value.indexOf('[');
        if (nbt >= 0) {
            value = value.substring(0, nbt);
        }
        return value;
    }

    private static String displayNeed(Need need) {
        if (need.label() != null && !need.label().isEmpty()) {
            return need.label();
        }
        if (need.itemIds().size() == 1) {
            return displayItem(need.itemId());
        }
        return need.itemIds().stream().map(AltoClefQuestBot::displayItem).collect(Collectors.joining(" OR "));
    }

    private static String displayItem(String itemId) {
        if (AWAKENED_ALLOY_BLOCK.equals(itemId)) {
            return "Awakened Unobtainium-Vibranium Alloy Block";
        }
        if (INFUSED_PATRICK_STAR.equals(itemId)) {
            return "Infused Patrick Star";
        }
        StarRecipe recipe = STAR_RECIPES.get(itemId);
        if (recipe != null) {
            return recipe.outputLabel();
        }
        return baseItemId(itemId);
    }

    private static List<String> autoMineTargets(String itemId) {
        String baseId = baseItemId(itemId);
        return mineTargets(baseId).stream()
                .filter(target -> isSafeAutoMineTarget(baseId, target))
                .toList();
    }

    private static boolean isSafeAutoMineTarget(String itemId, String target) {
        if (target.endsWith("_ore") || target.contains(":deepslate_") && target.endsWith("_ore")) {
            return true;
        }
        if (target.startsWith("alltheores:raw_") && target.endsWith("_block")) {
            return true;
        }
        if (target.equals(itemId) && isSafeDirectGatherBlock(itemId)) {
            return true;
        }
        return List.of(
                "allthemodium:ancient_stone",
                "minecraft:obsidian",
                "minecraft:netherrack",
                "minecraft:end_stone",
                "minecraft:dirt",
                "minecraft:sand",
                "minecraft:gravel",
                "minecraft:clay",
                "minecraft:ice",
                "minecraft:soul_soil",
                "minecraft:netherrack"
        ).contains(target) && target.equals(itemId);
    }

    private static boolean isSafeDirectGatherBlock(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return false;
        }

        String path = id.getPath();
        if (containsAny(path,
                "controller", "hatch", "altar", "forge", "machine", "generator", "reactor", "cable", "pipe",
                "chest", "barrel", "spawner", "tank", "cell", "drive", "bus", "terminal", "interface",
                "compressor", "crusher", "enricher", "infuser", "assembler", "casing", "quantum", "creative",
                "atm_star")) {
            return false;
        }
        if (path.endsWith("_block") && !containsAny(path, "crystal", "coral", "stone", "sand", "mud", "clay", "ice", "ore", "amethyst", "quartz")) {
            return false;
        }
        return containsAny(path,
                "ore", "stone", "grimstone", "sandstone", "sand", "gravel", "dirt", "mud", "clay",
                "moss", "velvetumoss", "grass", "leaves", "log", "wood", "stem", "hyphae", "vine",
                "roots", "cactus", "reed", "flower", "mushroom", "berry", "crystal", "coral", "kelp",
                "ice", "snow", "basalt", "netherrack", "end_stone", "obsidian", "deepslate", "dripstone",
                "calcite", "tuff", "amethyst", "quartz");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, StarRecipe> buildStarRecipes() {
        LinkedHashMap<String, StarRecipe> recipes = new LinkedHashMap<>();
        put(recipes, recipe(ATM_STAR, 1, "ATM Star", "MI Runic Star Altar 2048 EU/t 200t", "final local star_altar.js recipe",
                need(28, "allthemodium:unobtainium_allthemodium_alloy_block"),
                need(15, "allthecompressed:nether_star_block_3x"),
                needNamed(2, AWAKENED_ALLOY_BLOCK, "Awakened Unobtainium-Vibranium Alloy Block", "NBT custom name + Unbreaking I"),
                need(1, "allthetweaks:oblivion_shard"),
                need(1, "mysticalagradditions:creative_essence"),
                need(1, "allthetweaks:nexium_emitter"),
                need(1, "allthetweaks:withers_compass"),
                need(1, "allthetweaks:improbable_probability_device"),
                need(1, "allthetweaks:dragon_soul"),
                need(1, "allthetweaks:philosophers_fuel"),
                need(1, "allthetweaks:pulsating_black_hole"),
                need(1, "allthetweaks:dimensional_seed"),
                need(1, "allthetweaks:patrick_star")));

        put(recipes, recipe(AWAKENED_ALLOY_BLOCK, 1, "Awakened Unobtainium-Vibranium Alloy Block", "MI Runic Star Altar", "NBT output",
                need(1, "allthemodium:unobtainium_vibranium_alloy_block"),
                need(4, "mysticalagriculture:awakened_supremium_essence"),
                need(4, "minecraft:enchanted_book", "must be Unbreaking I"),
                need(4, "mysticalagriculture:awakened_supremium_gemstone")));

        put(recipes, recipe(INFUSED_PATRICK_STAR, 1, "Infused Patrick Star", "MI Runic Star Altar", "NBT output",
                need(36, "allthetweaks:atm_star_shard", "quest/reward gated"),
                need(12, "apothic_enchanting:infused_breath"),
                need(4, "minecraft:enchanted_book", "must be Mending I"),
                need(1, "allthemodium:vibranium_allthemodium_alloy_ingot"),
                need(1, "allthemodium:unobtainium_allthemodium_alloy_ingot"),
                need(1, "allthemodium:unobtainium_vibranium_alloy_ingot"),
                need(1, "allthetweaks:patrick_star")));

        put(recipes, recipe("allthetweaks:patrick_star", 1, "Patrick Star", "MI Runic Star Altar", "",
                need(13, "minecraft:magenta_concrete"),
                need(11, "minecraft:pink_concrete"),
                need(8, "minecraft:green_concrete"),
                need(8, "minecraft:green_concrete_powder"),
                need(8, "minecraft:magenta_concrete_powder"),
                need(5, "minecraft:pink_concrete_powder"),
                need(2, "minecraft:lime_concrete")));

        put(recipes, recipe("kubejs:atm_star_shard_1", 1, "ATM Star Fragment 1", "MI Runic Star Altar", "",
                need(48, "allthetweaks:atm_star_shard", "quest/reward gated"),
                need(1, "forbidden_arcanus:soul_binding_crystal")));
        put(recipes, recipe("kubejs:atm_star_shard_2", 1, "ATM Star Fragment 2", "MI Runic Star Altar", "",
                need(52, "allthetweaks:atm_star_shard", "quest/reward gated"),
                need(1, "forbidden_arcanus:whirlwind_prism")));
        put(recipes, recipe("kubejs:atm_star_shard_3", 1, "ATM Star Fragment 3", "MI Runic Star Altar", "",
                need(52, "allthetweaks:atm_star_shard", "quest/reward gated"),
                need(1, "forbidden_arcanus:smelter_prism")));
        put(recipes, recipe("kubejs:atm_star_shard_4", 1, "ATM Star Fragment 4", "MI Runic Star Altar", "",
                need(52, "allthetweaks:atm_star_shard", "quest/reward gated"),
                need(1, "forbidden_arcanus:sea_prism")));
        put(recipes, recipe("kubejs:atm_star_shard_5", 1, "ATM Star Fragment 5", "MI Runic Star Altar", "",
                need(52, "allthetweaks:atm_star_shard", "quest/reward gated"),
                need(1, "forbidden_arcanus:terrastomp_prism")));

        putComponentRecipes(recipes);
        putAlloyRecipes(recipes);
        putMiniPortalRecipes(recipes);
        putCompressionRecipes(recipes);
        return recipes;
    }

    private static void putComponentRecipes(Map<String, StarRecipe> recipes) {
        put(recipes, recipe("allthetweaks:dragon_soul", 1, "Dragon Soul", "shaped crafting", "",
                need(1, "apothic_enchanting:infused_breath"),
                need(1, "occultism:soul_gem"),
                needAlt(1, "Occultism dragon familiar", "creature catcher or spawn egg", "justdirethings:creaturecatcher", "occultism:spawn_egg/familiar_dragon"),
                need(1, "productivetrees:socotra_dragon_sapling"),
                need(1, "hostilenetworks:data_model", "Ender Dragon model with 1254/1255 data"),
                need(1, "allthemodium:piglich_heart_block"),
                need(1, "productivebees:spawn_egg_configurable_bee", "soul lava bee NBT"),
                need(1, "cataclysm:abyssal_sacrifice"),
                need(1, "eternal_starlight:chain_of_souls")));

        put(recipes, recipe("allthetweaks:improbable_probability_device", 1, "Improbable Probability Device", "shaped crafting", "",
                need(2, "mekanism:pellet_antimatter"),
                need(1, "ae2:singularity"),
                needAlt(2, "256M item cells OR MI blastproof casing", "either ingredient accepted", "megacells:portable_item_cell_256m", "modern_industrialization:blastproof_casing"),
                need(1, "pneumaticcraft:aerial_interface"),
                need(2, "ironfurnaces:million_furnace"),
                need(1, "irons_spellbooks:lightning_upgrade_orb")));

        put(recipes, recipe("allthetweaks:dimensional_seed", 1, "Dimensional Seed", "shaped crafting", "",
                need(1, "allthecompressed:netherrack_6x"),
                need(1, "allthecompressed:dirt_6x"),
                need(1, "allthecompressed:obsidian_5x"),
                need(1, "allthetweaks:mini_exit"),
                need(1, "allthetweaks:mini_nether"),
                need(1, "allthetweaks:mini_end"),
                need(1, "allthecompressed:end_stone_5x"),
                need(1, "allthecompressed:emerald_block_4x"),
                need(1, "allthecompressed:diamond_block_4x")));

        put(recipes, recipe("allthetweaks:withers_compass", 1, "Withers Compass", "shaped crafting", "",
                need(1, "productivebees:configurable_comb", "withered bee NBT"),
                need(1, "industrialforegoing:wither_builder"),
                need(1, "deeperdarker:heart_of_the_deep"),
                need(1, "generatorgalore:netherstar_generator_64x"),
                need(1, "irons_spellbooks:scroll", "Wither Skull level 10 NBT"),
                need(1, "mysticalagriculture:witherproof_bricks"),
                need(1, "minecraft:tipped_arrow", "long wither potion NBT"),
                need(1, "ars_nouveau:glyph_wither"),
                need(1, "mysticalagradditions:nether_star_crux")));

        put(recipes, recipe("allthetweaks:philosophers_fuel", 1, "Philosopher's Fuel", "shaped crafting", "",
                need(1, "generatorgalore:ender_generator"),
                need(1, "ironfurnaces:rainbow_coal"),
                need(1, "bigreactors:insanite_block"),
                needAlt(1, "MI uranium fuel rod quad OR Create blaze burner", "either ingredient accepted", "modern_industrialization:uranium_fuel_rod_quad", "create:blaze_burner"),
                need(1, "mysticalagradditions:insanium_coal_block"),
                need(1, "forbidden_arcanus:smelter_prism"),
                need(1, "mysticalagriculture:awakened_supremium_ingot_block"),
                need(1, "generatorgalore:magmatic_generator_64x"),
                need(1, "evilcraft:dark_tank", "filled with 16000mb refined T4 source")));

        put(recipes, recipe("allthetweaks:nexium_emitter", 1, "Nexium Emitter", "shaped crafting", "",
                need(1, "powah:player_transmitter_nitro"),
                need(1, "ae2wtlib:wireless_universal_terminal", "fully upgraded terminal NBT"),
                need(1, "mekanism:module_gravitational_modulating_unit"),
                needAlt(1, "MI large advanced motor OR Create mechanical arm", "either ingredient accepted", "modern_industrialization:large_advanced_motor", "create:mechanical_arm"),
                need(1, "immersiveengineering:tesla_coil"),
                need(1, "advanced_ae:quantum_multi_threader"),
                need(1, "aeinfinitybooster:infinity_card")));

        put(recipes, recipe("allthetweaks:oblivion_shard", 1, "Oblivion Shard", "shaped crafting", "",
                need(1, "forbidden_arcanus:eternal_stella"),
                need(1, "evilcraft:mace_of_destruction", "filled with 4000mb blood"),
                need(2, "evilcraft:piercing_vengeance_focus"),
                need(2, "stevescarts:module_galgadorian_drill"),
                need(1, "cataclysm:meat_shredder"),
                need(1, "twilightforest:snow_queen_trophy"),
                need(1, "cataclysm:void_forge")));

        put(recipes, recipe("allthetweaks:pulsating_black_hole", 1, "Pulsating Black Hole", "shaped crafting", "",
                need(1, "oritech:nuke"),
                need(1, "ae2:quantum_ring"),
                need(1, "pneumaticcraft:micromissiles"),
                need(1, "justdirethings:paradoxmachine"),
                need(1, "pocketstorage:psu_4"),
                need(1, "occultism:stable_wormhole"),
                need(1, "rootsclassic:crystal_staff"),
                need(1, "industrialforegoing:mycelial_explosive"),
                need(1, "evilcraft:lightning_bomb")));

        put(recipes, recipe("mysticalagradditions:creative_essence", 1, "Creative Essence", "shaped crafting", "",
                need(4, "mysticalagradditions:insanium_block"),
                need(4, "mysticalagradditions:insanium_gemstone_block"),
                need(1, "mysticalagriculture:master_infusion_crystal")));
    }

    private static void putAlloyRecipes(Map<String, StarRecipe> recipes) {
        put(recipes, recipe("allthemodium:unobtainium_allthemodium_alloy_block", 1, "Unobtainium-Allthemodium Alloy Block", "Ars Nouveau Enchanting Apparatus", "90000 source",
                need(2, "allthemodium:piglich_heart_block"),
                need(1, "kubejs:air_essence_block"),
                need(1, "allthemodium:allthemodium_block"),
                need(1, "kubejs:earth_essence_block"),
                need(1, "kubejs:fire_essence_block"),
                need(1, "allthemodium:unobtainium_block"),
                need(1, "kubejs:water_essence_block"),
                need(1, "ars_nouveau:source_gem_block")));

        put(recipes, recipe("allthemodium:unobtainium_allthemodium_alloy_ingot", 1, "Unobtainium-Allthemodium Alloy Ingot", "Ars Nouveau Enchanting Apparatus", "10000 source",
                need(2, "allthemodium:piglich_heart"),
                need(1, "ars_nouveau:air_essence"),
                need(1, "allthemodium:allthemodium_ingot"),
                need(1, "ars_nouveau:earth_essence"),
                need(1, "ars_nouveau:fire_essence"),
                need(1, "allthemodium:unobtainium_ingot"),
                need(1, "ars_nouveau:water_essence"),
                need(1, "ars_nouveau:source_gem")));

        put(recipes, recipe("allthemodium:unobtainium_vibranium_alloy_block", 1, "Unobtainium-Vibranium Alloy Block", "Industrial Foregoing Dissolution Chamber", "900mb soul lava",
                need(4, "industrialforegoing:pink_slime_block"),
                need(1, "allthemodium:vibranium_block"),
                need(2, "allthemodium:piglich_heart_block"),
                need(1, "allthemodium:unobtainium_block")));

        put(recipes, recipe("allthemodium:unobtainium_vibranium_alloy_ingot", 1, "Unobtainium-Vibranium Alloy Ingot", "Industrial Foregoing Dissolution Chamber", "100mb soul lava",
                need(4, "industrialforegoing:pink_slime"),
                need(1, "allthemodium:vibranium_ingot"),
                need(2, "allthemodium:piglich_heart"),
                need(1, "allthemodium:unobtainium_ingot")));

        put(recipes, recipe("allthemodium:vibranium_allthemodium_alloy_block", 1, "Vibranium-Allthemodium Alloy Block", "Powah Energizing", "9000000000 FE",
                need(1, "allthemodium:allthemodium_block"),
                need(2, "allthemodium:piglich_heart_block"),
                need(1, "allthecompressed:nitro_crystal_block_2x"),
                need(1, "allthemodium:vibranium_block")));

        put(recipes, recipe("allthemodium:vibranium_allthemodium_alloy_ingot", 1, "Vibranium-Allthemodium Alloy Ingot", "Powah Energizing", "1000000000 FE",
                need(1, "allthemodium:allthemodium_ingot"),
                need(2, "allthemodium:piglich_heart"),
                need(1, "allthecompressed:nitro_crystal_block_1x"),
                need(1, "allthemodium:vibranium_ingot")));
    }

    private static void putMiniPortalRecipes(Map<String, StarRecipe> recipes) {
        put(recipes, recipe("allthetweaks:mini_nether", 1, "Mini Nether", "shaped crafting", "",
                need(4, "minecraft:obsidian"),
                need(2, "minecraft:nether_star"),
                need(1, "apothic_enchanting:sightshelf_t2"),
                need(1, "minecraft:wither_skeleton_skull"),
                need(1, "minecraft:warped_nylium")));
        put(recipes, recipe("allthetweaks:mini_end", 1, "Mini End", "shaped crafting", "",
                need(4, "apothic_enchanting:endshelf"),
                need(4, "minecraft:ender_eye"),
                need(1, "apothic_enchanting:draconic_endshelf")));
        put(recipes, recipe("allthetweaks:mini_exit", 1, "Mini Exit", "shaped crafting", "",
                need(4, "minecraft:dragon_egg"),
                need(1, "apothic_enchanting:infused_breath"),
                need(1, "apothic_enchanting:soul_touched_sculkshelf"),
                need(3, "minecraft:end_crystal")));
    }

    private static void putCompressionRecipes(Map<String, StarRecipe> recipes) {
        putCompressed(recipes, "allthecompressed:nether_star_block_3x", "Nether Star Block 3x", "allthetweaks:nether_star_block", 3);
        putCompressed(recipes, "allthecompressed:netherrack_6x", "Netherrack 6x", "minecraft:netherrack", 6);
        putCompressed(recipes, "allthecompressed:dirt_6x", "Dirt 6x", "minecraft:dirt", 6);
        putCompressed(recipes, "allthecompressed:obsidian_5x", "Obsidian 5x", "minecraft:obsidian", 5);
        putCompressed(recipes, "allthecompressed:end_stone_5x", "End Stone 5x", "minecraft:end_stone", 5);
        putCompressed(recipes, "allthecompressed:emerald_block_4x", "Emerald Block 4x", "minecraft:emerald_block", 4);
        putCompressed(recipes, "allthecompressed:diamond_block_4x", "Diamond Block 4x", "minecraft:diamond_block", 4);
        putCompressed(recipes, "allthecompressed:nitro_crystal_block_1x", "Nitro Crystal Block 1x", "powah:nitro_crystal_block", 1);
        putCompressed(recipes, "allthecompressed:nitro_crystal_block_2x", "Nitro Crystal Block 2x", "powah:nitro_crystal_block", 2);

        put(recipes, recipe("allthetweaks:nether_star_block", 1, "Nether Star Block", "vanilla-style compacting", "",
                need(9, "minecraft:nether_star")));
        put(recipes, recipe("minecraft:diamond_block", 1, "Diamond Block", "vanilla compacting", "",
                need(9, "minecraft:diamond")));
        put(recipes, recipe("minecraft:emerald_block", 1, "Emerald Block", "vanilla compacting", "",
                need(9, "minecraft:emerald")));
        put(recipes, recipe("allthemodium:allthemodium_block", 1, "Allthemodium Block", "vanilla compacting", "",
                need(9, "allthemodium:allthemodium_ingot")));
        put(recipes, recipe("allthemodium:vibranium_block", 1, "Vibranium Block", "vanilla compacting", "",
                need(9, "allthemodium:vibranium_ingot")));
        put(recipes, recipe("allthemodium:unobtainium_block", 1, "Unobtainium Block", "vanilla compacting", "",
                need(9, "allthemodium:unobtainium_ingot")));
        put(recipes, recipe("allthemodium:piglich_heart_block", 1, "Piglich Heart Block", "vanilla compacting", "",
                need(9, "allthemodium:piglich_heart")));
    }

    private static void putCompressed(Map<String, StarRecipe> recipes, String outputId, String label, String baseItem, int level) {
        put(recipes, recipe(outputId, 1, label, "AllTheCompressed compacting", "modeled as 9^" + level + " base items",
                need(pow9(level), baseItem)));
    }

    private static long pow9(int level) {
        long value = 1;
        for (int i = 0; i < level; i++) {
            value *= 9;
        }
        return value;
    }

    private static void put(Map<String, StarRecipe> recipes, StarRecipe recipe) {
        recipes.put(recipe.outputId(), recipe);
    }

    private static StarRecipe recipe(String outputId, long outputCount, String label, String machine, String note, Need... inputs) {
        return new StarRecipe(outputId, outputCount, label, machine, note, List.of(inputs));
    }

    private static Need need(long count, String itemId) {
        return new Need(count, List.of(itemId), null, "");
    }

    private static Need need(long count, String itemId, String note) {
        return new Need(count, List.of(itemId), null, note);
    }

    private static Need needNamed(long count, String itemId, String label, String note) {
        return new Need(count, List.of(itemId), label, note);
    }

    private static Need needAlt(long count, String label, String note, String... itemIds) {
        return new Need(count, List.of(itemIds), label, note);
    }

    private static List<MachinePlan> machinePlans() {
        return List.of(
                new MachinePlan("Automatic Hephaestus Forge", "minimum hatches: item input, item output, fluid input, energy input", autoForgeMaterials()),
                new MachinePlan("Runic Crucible", "minimum hatches: item input, item output, fluid input, fluid output, energy input", runicCrucibleMaterials()),
                new MachinePlan("Runic Enchanter", "minimum hatches: item input, item output, fluid input, energy input", runicEnchanterMaterials()),
                new MachinePlan("Runic Star Altar", "minimum hatches: item input, item output, energy input", starAltarMaterials())
        );
    }

    private static void increment(Map<String, Integer> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }

    private static boolean isStarTask(Task task) {
        String chapter = task.getQuestChapter().getFilename().toLowerCase();
        return chapter.contains("star");
    }

    private static String taskType(Task task) {
        try {
            ResourceLocation id = task.getType().getTypeId();
            return id == null ? task.getClass().getSimpleName() : id.toString();
        } catch (Throwable ignored) {
            return task.getClass().getSimpleName();
        }
    }

    private static List<Task> sortedTasks(ClientQuestFile file) {
        return file.getAllTasks().stream()
                .sorted(Comparator.comparingLong(task -> task.id))
                .toList();
    }

    private static int countMatching(LocalPlayer player, ItemTask task) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (task.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static List<String> mineTargets(String itemId) {
        String normalized = itemId.trim();
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }

        ResourceLocation item = ResourceLocation.tryParse(normalized);
        if (item == null) {
            return List.of();
        }

        String namespace = item.getNamespace();
        String path = item.getPath();
        ArrayList<String> candidates = new ArrayList<>();
        add(candidates, namespace + ":" + path);

        String base = path;
        for (String suffix : List.of("_ingot", "_gem", "_dust", "_nugget", "_chunk", "_piece", "_essence", "_block")) {
            if (base.endsWith(suffix)) {
                base = base.substring(0, base.length() - suffix.length());
                break;
            }
        }
        if (base.startsWith("raw_")) {
            base = base.substring(4);
        }

        add(candidates, namespace + ":" + base + "_ore");
        add(candidates, namespace + ":deepslate_" + base + "_ore");
        add(candidates, namespace + ":raw_" + base + "_block");
        add(candidates, "alltheores:" + base + "_ore");
        add(candidates, "alltheores:deepslate_" + base + "_ore");
        add(candidates, "alltheores:raw_" + base + "_block");
        add(candidates, "allthemodium:" + base + "_ore");
        add(candidates, "allthemodium:deepslate_" + base + "_ore");
        add(candidates, "allthemodium:other_" + base + "_ore");
        add(candidates, "minecraft:" + base + "_ore");
        add(candidates, "minecraft:deepslate_" + base + "_ore");
        add(candidates, "minecraft:" + base + "_block");
        if (base.equals("allthemodium")) {
            add(candidates, "allthemodium:allthemodium_ore");
            add(candidates, "allthemodium:allthemodium_slate_ore");
            add(candidates, "allthemodium:deepslate_allthemodium_ore");
            add(candidates, "allthemodium:ancient_stone");
        } else if (base.equals("vibranium")) {
            add(candidates, "allthemodium:vibranium_ore");
            add(candidates, "allthemodium:other_vibranium_ore");
        } else if (base.equals("unobtainium")) {
            add(candidates, "allthemodium:unobtainium_ore");
        } else if (base.equals("quartz")) {
            add(candidates, "minecraft:nether_quartz_ore");
        }

        ArrayList<String> present = new ArrayList<>();
        for (String candidate : candidates) {
            ResourceLocation blockId = ResourceLocation.tryParse(candidate);
            if (blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId) && !present.contains(candidate)) {
                present.add(candidate);
            }
        }
        return present;
    }

    private static Map<String, Integer> starAltarMaterials() {
        LinkedHashMap<String, Integer> materials = new LinkedHashMap<>();
        materials.put("modern_industrialization:star_altar", 1);
        materials.put("forbidden_arcanus:polished_darkstone_stairs", 112);
        materials.put("forbidden_arcanus:polished_darkstone", 322);
        materials.put("forbidden_arcanus:gilded_chiseled_polished_darkstone", 17);
        materials.put("forbidden_arcanus:polished_darkstone_slab", 20);
        materials.put("forbidden_arcanus:arcane_polished_darkstone", 8);
        materials.put("forbidden_arcanus:chiseled_arcane_polished_darkstone", 4);
        materials.put("forbidden_arcanus:rune_block", 16);
        materials.put("forbidden_arcanus:arcane_polished_darkstone_pillar", 40);
        materials.put("forbidden_arcanus:arcane_crystal_block", 4);
        materials.put("forbidden_arcanus:darkstone_pedestal", 8);
        materials.put("forbidden_arcanus:essence_utrem_jar", 4);
        materials.put("forbidden_arcanus:magnetized_darkstone_pedestal", 1);
        materials.put("forbidden_arcanus:quantum_injector", 1);
        materials.put("forbidden_arcanus:arcane_crystal_obelisk", 8);
        materials.put("modern_industrialization:runic_item_input_hatch", 1);
        materials.put("modern_industrialization:runic_item_output_hatch", 1);
        materials.put("modern_industrialization:runic_energy_input_hatch", 1);
        return materials;
    }

    private static Map<String, Integer> autoForgeMaterials() {
        LinkedHashMap<String, Integer> materials = new LinkedHashMap<>();
        materials.put("modern_industrialization:auto_forge", 1);
        materials.put("forbidden_arcanus:polished_darkstone_stairs", 32);
        materials.put("forbidden_arcanus:polished_darkstone", 132);
        materials.put("forbidden_arcanus:polished_darkstone_slab", 24);
        materials.put("forbidden_arcanus:gilded_chiseled_polished_darkstone", 9);
        materials.put("forbidden_arcanus:rune_block", 4);
        materials.put("forbidden_arcanus:darkstone_pedestal", 8);
        materials.put("forbidden_arcanus:arcane_polished_darkstone", 8);
        materials.put("forbidden_arcanus:chiseled_arcane_polished_darkstone", 4);
        materials.put("forbidden_arcanus:arcane_crystal_obelisk", 4);
        materials.put("forbidden_arcanus:quantum_injector", 1);
        materials.put("forbidden_arcanus:hephaestus_forge_tier_5", 1);
        materials.put("modern_industrialization:runic_item_input_hatch", 1);
        materials.put("modern_industrialization:runic_item_output_hatch", 1);
        materials.put("modern_industrialization:runic_fluid_input_hatch", 1);
        materials.put("modern_industrialization:runic_energy_input_hatch", 1);
        return materials;
    }

    private static Map<String, Integer> runicCrucibleMaterials() {
        LinkedHashMap<String, Integer> materials = new LinkedHashMap<>();
        materials.put("modern_industrialization:runic_crucible", 1);
        materials.put("forbidden_arcanus:polished_darkstone_stairs", 19);
        materials.put("forbidden_arcanus:polished_darkstone", 11);
        materials.put("forbidden_arcanus:gilded_chiseled_polished_darkstone", 1);
        materials.put("forbidden_arcanus:rune_block", 4);
        materials.put("forbidden_arcanus:arcane_polished_darkstone_pillar", 4);
        materials.put("forbidden_arcanus:arcane_polished_darkstone", 4);
        materials.put("forbidden_arcanus:arcane_crystal_block", 5);
        materials.put("forbidden_arcanus:quantum_injector", 1);
        materials.put("modern_industrialization:runic_item_input_hatch", 1);
        materials.put("modern_industrialization:runic_item_output_hatch", 1);
        materials.put("modern_industrialization:runic_fluid_input_hatch", 1);
        materials.put("modern_industrialization:runic_fluid_output_hatch", 1);
        materials.put("modern_industrialization:runic_energy_input_hatch", 1);
        return materials;
    }

    private static Map<String, Integer> runicEnchanterMaterials() {
        LinkedHashMap<String, Integer> materials = new LinkedHashMap<>();
        materials.put("modern_industrialization:runic_enchanter", 1);
        materials.put("forbidden_arcanus:polished_darkstone_stairs", 35);
        materials.put("forbidden_arcanus:polished_darkstone", 69);
        materials.put("forbidden_arcanus:polished_darkstone_slab", 20);
        materials.put("forbidden_arcanus:gilded_chiseled_polished_darkstone", 9);
        materials.put("forbidden_arcanus:rune_block", 16);
        materials.put("forbidden_arcanus:darkstone_pedestal", 8);
        materials.put("forbidden_arcanus:arcane_polished_darkstone_pillar", 12);
        materials.put("forbidden_arcanus:arcane_polished_darkstone", 12);
        materials.put("forbidden_arcanus:arcane_crystal_block", 12);
        materials.put("forbidden_arcanus:quantum_injector", 1);
        materials.put("minecraft:enchanting_table", 1);
        materials.put("apothic_enchanting:soul_touched_sculkshelf", 10);
        materials.put("apothic_enchanting:soul_touched_deepshelf", 30);
        materials.put("apothic_enchanting:echoing_sculkshelf", 4);
        materials.put("minecraft:soul_lantern", 4);
        materials.put("modern_industrialization:runic_item_input_hatch", 1);
        materials.put("modern_industrialization:runic_item_output_hatch", 1);
        materials.put("modern_industrialization:runic_fluid_input_hatch", 1);
        materials.put("modern_industrialization:runic_energy_input_hatch", 1);
        return materials;
    }

    private static void add(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static ClientQuestFile questFile() {
        return ClientQuestFile.exists() ? ClientQuestFile.INSTANCE : null;
    }

    private static TeamData teamData(ClientQuestFile file) {
        return file == null ? null : file.selfTeamData;
    }

    private static boolean runBaritone(String command) {
        try {
            Class<?> api = Class.forName("baritone.c");
            Object provider = invokeNoArgByReturn(api, null, "baritone.api.IBaritoneProvider");
            Object baritone = invokeNoArgByReturn(provider.getClass(), provider, "baritone.d");
            Object commandManager = invokeNoArgByReturn(baritone.getClass(), baritone, "baritone.hm");
            Method execute = methodByReturn(commandManager.getClass(), "a", boolean.class, String.class);
            Object result = execute.invoke(commandManager, command);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object invokeNoArgByReturn(Class<?> type, Object target, String returnType) throws Exception {
        for (Method method : type.getMethods()) {
            if (method.getName().equals("a") && method.getParameterCount() == 0 && method.getReturnType().getName().equals(returnType)) {
                return method.invoke(target);
            }
        }
        throw new NoSuchMethodException(returnType);
    }

    private static Method methodByReturn(Class<?> type, String name, Class<?> returnType, Class<?>... params) throws Exception {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getReturnType().equals(returnType) && sameParams(method, params)) {
                return method;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static boolean sameParams(Method method, Class<?>[] params) {
        Class<?>[] actual = method.getParameterTypes();
        if (actual.length != params.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (!actual[i].equals(params[i])) {
                return false;
            }
        }
        return true;
    }

    private static void say(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }

    private enum GoalMode {
        ATM_STAR("atm_star"),
        ALL_QUESTS("all_quests");

        private final String label;

        GoalMode(String label) {
            this.label = label;
        }
    }

    private record QuestTarget(long taskId, String itemId, String questTitle, long required, long progress) {
    }

    private record QuestStep(long taskId, String questTitle, String type, String action, double questX, double questY) {
    }

    private record RouteGate(String dimension, String action) {
    }

    private record StarBlocker(String itemId, String label, long required, long have, String action, boolean machineStep) {
    }

    private record StarRecipe(String outputId, long outputCount, String outputLabel, String machine, String note, List<Need> inputs) {
    }

    private record Need(long count, List<String> itemIds, String label, String note) {
        String itemId() {
            return itemIds.get(0);
        }

        Need withCount(long value) {
            return new Need(value, itemIds, label, note);
        }
    }

    private record MachinePlan(String name, String note, Map<String, Integer> materials) {
    }

    private record LeafNeed(Need need, long required) {
    }
}
