package com.local.altoclef;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasks.construction.ClearRegionTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.EntityHelper;
import adris.altoclef.control.KillAura;
import adris.altoclef.util.helpers.LookHelper;
import baritone.api.BaritoneAPI;
import baritone.api.utils.input.Input;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Full warden cheese strategy:
 *
 *   (optional) GATHER  — bow + arrows, iron blocks + carved pumpkins
 *   FIND_SHRIEKER      — locate nearest sculk shrieker
 *   BUILD_GOLEMS       — place golem structures near shrieker (if have mats)
 *   TRIGGER            — stop sneaking for one tick → shrieker fires → warden spawns
 *   SPRINT_AND_DIG     — rush to emerging warden, mine 2×2 pit around its feet
 *                        (30-second emerge window via isDiggingOrEmerging())
 *   RETREAT            — get 21+ blocks away
 *   FIGHT              — bow aimbot on; melee finish when warden is low
 *
 * "Make do": if already in the deep dark skip GATHER; build golems only if
 * iron/pumpkins are already in inventory.
 */
public class WardenTrapTask extends Task {

    /** True while this task is running — suppresses MobDefenseChain warden flee. */
    public static boolean isActive = false;

    // ── Config ───────────────────────────────────────────────────────────────
    private static final int    DEFAULT_RETREAT_DIST = 28;
    private static final int    DEFAULT_GOLEM_COUNT = 4;
    private static final int    DEFAULT_BOW_ARROWS_NEEDED = 64; // warden=500HP; plain bow ~6dmg/shot = ~83 shots; Power V = 25dmg = 20 shots
    private static final int    DEFAULT_TOWER_HEIGHT = 8;   // opportunistic only — not mandatory
    private static final int    DEFAULT_MAX_RETRAPS = 3;
    private static final int    BUILD_BLOCKS_NEEDED = 16;  // short pillar only
    private static final int    RETRAP_DIST       = 48;
    private static final int    TOWER_STALL_TICKS = 120;
    private static final int    VERIFY_CLEAR_TICKS = 100;
    private static final int    PHASE_STALL_TICKS = 200;
    private static final float  FINISH_HP         = 80;   // switch to melee finish below this
    private static final int    DIG_RADIUS        = 1;    // mine 2×2 around warden feet
    private static final int    DIG_DEPTH         = 2;    // pit depth
    // Wool cage: 4 sides × 3 heights = 12 blocks placed 1 out from warden spawn centre
    // Wool absorbs vibrations — caged warden can't detect player footsteps/attacks
    private static final int[][] CAGE_OFFSETS = {
        {-1,0,0},{1,0,0},{0,0,-1},{0,0,1},   // sides at foot level
        {-1,1,0},{1,1,0},{0,1,-1},{0,1,1},   // sides at chest level
        {-1,2,0},{1,2,0},{0,2,-1},{0,2,1},   // sides at head level
    };
    private static final int WOOL_NEEDED = 12;
    // Sonic boom: 15-block range, 40-tick windup. We target 17b to have margin.
    private static final double SONIC_BOOM_SAFE_DIST = 22.0;
    private static final double SONIC_BOOM_DODGE_DIST = 20.0;
    private static final int SHRIEK_DODGE_MAX_TICKS = 80;
    private static final int SHRIEK_DODGE_IGNORE_TICKS = 60;
    private static final int UNDERMINE_TUNNEL_DEPTH = 5;
    private static final int UNDERMINE_ATTACK_DEPTH = 2;
    private static final int UNDERMINE_ATTACK_DRIFT = 1;
    private static final int UNDERMINE_CLEAR_COOLDOWN = 20;
    private static final int UNDERMINE_START_EXTRA_DIST = 12;
    // Warden melee swing: dodge window in pit-edge combat
    private static final double MELEE_SAFE_DIST = 4.5;

    // Golem T-structure offsets: indices 0-3 = iron blocks, index 4 = pumpkin (placed last)
    private static final int[][] GOLEM_OFFSETS = {
        {0, 0, 0}, {-1, 1, 0}, {0, 1, 0}, {1, 1, 0}, {0, 2, 0}
    };

    private static int retreatDist = DEFAULT_RETREAT_DIST;
    private static int golemCount = DEFAULT_GOLEM_COUNT;
    private static int bowArrowsNeeded = DEFAULT_BOW_ARROWS_NEEDED;
    private static int towerHeight = DEFAULT_TOWER_HEIGHT;
    private static int maxRetraps = DEFAULT_MAX_RETRAPS;
    private static volatile OverrideCommand pendingOverride = OverrideCommand.NONE;
    private static String lastStatus = "WardenTrap: idle";

    private final boolean gatherFirst; // if false, use whatever is in inventory

    // ── State ────────────────────────────────────────────────────────────────
    private Phase       phase        = Phase.FIND_SHRIEKER;
    private BlockPos    shriekerPos  = null;
    private Warden      warden       = null;
    private BlockPos    wardenSpawn  = null;
    private int         golemsBuilt  = 0;
    private int         golemStep    = 0;
    private int         triggerTimer = 0;  // ticks since trigger
    private List<BlockPos> digTargets = new ArrayList<>();
    private int         digIndex     = 0;
    private int         golemBuildTarget = 0;
    private boolean     trapDug      = false;
    private BlockPos    towerBase    = null;
    private BlockPos    plannedTower = null;
    private BlockPos    losFailBlock = null;
    private BlockPos    lastWardenPos = null;
    // WOOL_CAGE state
    private int         cageStep     = 0;
    private boolean     woolCaged    = false;   // true once cage was completed
    // LURE_PIT state
    private BlockPos    lureDigPos   = null;   // centre of the pit we're digging
    private BlockPos    lureBaitPos  = null;   // where player walks to generate warden aggro
    private BlockPos    lureHoldPos  = null;   // where player sprints to after aggro
    private List<BlockPos> lureDig   = new ArrayList<>();
    private int         lureDigIdx   = 0;
    private int         lureBaitTicks = 0;     // how long we've been at bait pos
    private int         lureHoldTicks = 0;
    private int         towerTimer   = 0;
    private int         lastTowerHeight = 0;
    private int         towerStallTicks = 0;
    private int         retrapAttempts = 0;
    private BlockPos    shriekFleeTarget = null;  // locked flee position for current shriek; cleared when animation ends
    private int         shriekDodgeTicks = 0;
    private int         shriekIgnoreTicks = 0;
    private BlockPos    undermineTarget = null;
    private BlockPos    undermineLastClearA = null;
    private BlockPos    undermineLastClearB = null;
    private int         undermineClearCooldown = 0;
    private int         retreatStuckTicks = 0;
    private double      lastRetreatDist = -1.0D;
    private boolean     returnToLureAfterRetreat = false;
    private Phase       postRetreatPhase = Phase.FIGHT;  // what to enter when RETREAT finishes
    private int         shriekBackTicks = -1;  // countdown to /back after /home dodge; -1 = idle
    private boolean     pitConfirmed = false;  // latched true once warden is seen in pit; cleared only after 10 ticks out
    private int         pitEscapeTicks = 0;   // consecutive ticks isWardenInPit returned false
    private int         missingWardenTicks = 0;
    private int         golemRepairCooldown = 0;
    private int         phaseTicks = 0;
    private int         statusTicks = 0;
    private int         particleTicks = 0;
    private boolean     complete = false;
    private String      lastFailReason = "none";
    private Phase       lastPhase = null;
    private final List<BlockPos> badShriekers = new ArrayList<>();

    public WardenTrapTask(boolean gatherFirst) {
        this.gatherFirst = gatherFirst;
    }

    public static String statusLine() {
        return lastStatus;
    }

    public static void requestFightOverride() {
        pendingOverride = OverrideCommand.FIGHT;
    }

    public static void requestRetrapOverride() {
        pendingOverride = OverrideCommand.RETRAP;
    }

    public static void requestTowerOverride() {
        pendingOverride = OverrideCommand.TOWER;
    }

    public static String configLine() {
        return "retreat=" + retreatDist
                + " tower=" + towerHeight
                + " arrows=" + bowArrowsNeeded
                + " golems=" + golemCount
                + " retraps=" + maxRetraps;
    }

    public static String planLine(AltoClef mod) {
        if (mod == null || mod.getPlayer() == null || mod.getWorld() == null) return "Warden plan: no world";
        int arrows = mod.getItemStorage().getItemCountInventoryOnly(Items.ARROW);
        int blocks = PlaceBlockTask.getMaterialCount(mod);
        int golems = availableGolems(mod);
        BlockPos shrieker = nearestShriekerLoose(mod);
        String weapon = bestWeaponName(mod);
        boolean ranged = hasRangedWeapon(mod);
        return "Warden plan: weapon=" + weapon + "(ranged=" + ranged + ")"
                + " arrows=" + arrows + "/" + bowArrowsNeeded
                + " blocks=" + blocks + "(opt)"
                + " golems=" + golems + "/" + golemCount
                + " shrieker=" + (shrieker == null ? "none" : shrieker.toShortString())
                + " retreat=" + retreatDist;
    }

    public static String configure(String key, int value) {
        switch (key) {
            case "retreat" -> retreatDist = clamp(value, 24, 48);
            case "tower" -> towerHeight = clamp(value, 4, 40);
            case "arrows" -> bowArrowsNeeded = clamp(value, 1, 256);
            case "golems" -> golemCount = clamp(value, 0, 12);
            case "retraps" -> maxRetraps = clamp(value, 0, 10);
            default -> { return "unknown config: " + key; }
        }
        return configLine();
    }

    private enum Phase {
        GATHER, FIND_SHRIEKER, BUILD_GOLEMS, TRIGGER,
        SPRINT_AND_DIG, WOOL_CAGE, RETRAP_RETREAT, RETREAT, TOWER, LURE_PIT, FIGHT, UNDERMINE, VERIFY_CLEAR
    }

    private enum OverrideCommand {
        NONE, FIGHT, RETRAP, TOWER
    }

    // ── Task lifecycle ───────────────────────────────────────────────────────

    @Override
    protected void onStart(AltoClef mod) {
        isActive = true;
        phase = gatherFirst ? Phase.GATHER : Phase.FIND_SHRIEKER;
        shriekerPos = null;
        warden = null;
        wardenSpawn = null;
        golemsBuilt = 0;
        golemStep = 0;
        triggerTimer = 0;
        digTargets.clear();
        digIndex = 0;
        golemBuildTarget = 0;
        trapDug = false;
        towerBase = null;
        plannedTower = null;
        losFailBlock = null;
        lastWardenPos = null;
        cageStep = 0;
        woolCaged = false;
        lureDigPos = null;
        lureBaitPos = null;
        lureHoldPos = null;
        lureDig.clear();
        lureDigIdx = 0;
        lureBaitTicks = 0;
        lureHoldTicks = 0;
        towerTimer = 0;
        lastTowerHeight = 0;
        towerStallTicks = 0;
        retrapAttempts = 0;
        shriekFleeTarget = null;
        shriekDodgeTicks = 0;
        shriekIgnoreTicks = 0;
        resetUndermineState();
        retreatStuckTicks = 0;
        lastRetreatDist = -1.0D;
        returnToLureAfterRetreat = false;
        postRetreatPhase = Phase.FIGHT;
        shriekBackTicks = -1;
        pitConfirmed = false;
        pitEscapeTicks = 0;
        missingWardenTicks = 0;
        golemRepairCooldown = 0;
        phaseTicks = 0;
        statusTicks = 0;
        particleTicks = 0;
        complete = false;
        lastFailReason = "none";
        lastPhase = null;
        badShriekers.clear();
        pendingOverride = OverrideCommand.NONE;
        BowAimbot.clearForcedTarget();
        say("Warden trap: starting (" + (gatherFirst ? "gather first" : "make do") + ")");
    }

    @Override
    protected Task onTick(AltoClef mod) {
        tickBookkeeping(mod);
        Warden nearest = nearestWarden(mod);
        if (nearest != null) {
            missingWardenTicks = 0;
            // Don't switch tracked warden to a free one while we're actively fighting a contained warden.
            // Switching would make the FIGHT phase see inPit=false and start retreating without sneak,
            // triggering more shriekers and spawning infinite wardens.
            boolean fightingContained = (phase == Phase.FIGHT || phase == Phase.TOWER)
                    && warden != null && warden.isAlive()
                    && (isWardenInPit(mod, warden) || woolCaged);
            if (!fightingContained) {
                if (warden == null || !warden.isAlive()
                        || nearest.distanceTo(mod.getPlayer()) + 8.0D < warden.distanceTo(mod.getPlayer())) {
                    warden = nearest;
                    logFail("tracking warden " + warden.getId() + " hp=" + (int) warden.getHealth());
                }
            }
            lastWardenPos = warden.blockPosition();
            // Warden is already alive — skip pre-fight phases immediately
            if (phase == Phase.GATHER || phase == Phase.FIND_SHRIEKER
                    || phase == Phase.BUILD_GOLEMS || phase == Phase.TRIGGER
                    || phase == Phase.SPRINT_AND_DIG) {
                AncientCityHelper.setManualSneak(false);
                releaseTowerInputs(mod);
                double dist = warden.distanceTo(mod.getPlayer());
                // If already trapped, just retreat and fight. Otherwise lure it into a pit.
                if (isWardenInPit(mod, warden) || woolCaged) {
                    phase = Phase.FIGHT;
                    equipBestRangedWeapon(mod);
                    BowAimbot.setForcedTarget(warden);
                    BowAimbot.setEnabled(hasRangedWeapon(mod));
                    say("Warden already alive and trapped (dist=" + (int) dist + "b) — fighting.");
                } else {
                    phase = Phase.RETREAT;
                    resetUndermineState();
                    say("Warden already alive (dist=" + (int) dist + "b) — retreating before tunnel.");
                }
            }
        } else if (phase == Phase.FIGHT || phase == Phase.TOWER || phase == Phase.RETREAT || phase == Phase.UNDERMINE) {
            missingWardenTicks++;
            if (missingWardenTicks > VERIFY_CLEAR_TICKS) {
                warden = null;
                phase = Phase.VERIFY_CLEAR;
                logFail("warden missing; verify clear");
            }
        }

        // ── Attack dodge: /home on ANY warden attack, /back 10s later ──────
        // Melee swing OR sonic boom → teleport home, return after delay, retreat first.
        if (mod.getPlayer() != null) {
            if (shriekIgnoreTicks > 0) shriekIgnoreTicks--;
            // /back countdown runs unconditionally (we're at home so wardens are gone)
            if (shriekBackTicks > 0) {
                shriekBackTicks--;
                if (shriekBackTicks == 0) {
                    Minecraft mc2 = Minecraft.getInstance();
                    if (mc2.getConnection() != null) {
                        mc2.getConnection().sendUnsignedCommand("back");
                        say("Returning via /back — retreating first.");
                    }
                    shriekFleeTarget = null;
                    shriekDodgeTicks = 0;
                    shriekIgnoreTicks = SHRIEK_DODGE_IGNORE_TICKS;
                    // Retreat to safe distance before resuming fight
                    if (phase == Phase.FIGHT || phase == Phase.TOWER || phase == Phase.RETREAT) {
                        postRetreatPhase = Phase.FIGHT;
                        phase = Phase.RETREAT;
                    }
                }
            }
            List<Warden> allAlive = allWardens(mod);
            // Trigger on sonic boom windup OR melee swing within range
            Warden attackThreat = shriekIgnoreTicks > 0 ? null : allAlive.stream()
                    .filter(w -> isActiveSonicThreat(mod, w) || isWardenMeleeClose(mod, w))
                    .min(Comparator.comparingDouble(w -> w.distanceTo(mod.getPlayer())))
                    .orElse(null);

            if (attackThreat != null) {
                if (shriekFleeTarget == null) {
                    shriekFleeTarget = attackThreat.blockPosition();
                    shriekDodgeTicks = 0;
                    releaseTowerInputs(mod);
                    BowAimbot.setEnabled(false);
                    boolean isSonic = isActiveSonicThreat(mod, attackThreat);
                    say((isSonic ? "SONIC BOOM" : "MELEE") + " DODGE! /home — warden#" + attackThreat.getId());
                    EmergencyHome.goHome("warden attack dodge");
                    shriekBackTicks = 300;
                }
                shriekDodgeTicks++;
                return null;
            } else {
                if (allAlive.stream().noneMatch(w -> isActiveSonicThreat(mod, w) || isWardenMeleeClose(mod, w))) {
                    shriekFleeTarget = null;
                    shriekDodgeTicks = 0;
                }
            }

            // Second free warden threatening us while we fight a contained one
            if (warden != null && warden.isAlive()
                    && (phase == Phase.FIGHT || phase == Phase.TOWER)
                    && (isWardenInPit(mod, warden) || woolCaged)) {
                for (Warden w2 : allAlive) {
                    if (w2 == warden || !w2.isAlive()) continue;
                    double d2 = w2.distanceTo(mod.getPlayer());
                    if (d2 < retreatDist) {
                        BowAimbot.setEnabled(false);
                        AncientCityHelper.setManualSneak(false);
                        BlockPos flee = safeRetreatPos(mod, w2, retreatDist + 8);
                        setDebugState("SECOND WARDEN#" + w2.getId() + " at " + (int) d2
                                + "b — retreating (main warden contained)");
                        say("Second warden too close — retreating from it.");
                        return new GetToBlockTask(flee);
                    }
                }
            }
        }

        Task overrideTask = handleOverride(mod);
        if (overrideTask != null) return overrideTask;
        publishStatus(mod);
        renderMarkers(mod);

        switch (phase) {

            // ── GATHER ───────────────────────────────────────────────────────
            case GATHER -> {
                // Bow or crossbow
                if (!mod.getItemStorage().hasItemInventoryOnly(Items.BOW) &&
                    !mod.getItemStorage().hasItemInventoryOnly(Items.CROSSBOW)) {
                    setDebugState("Collecting bow");
                    return new CollectItemTask(new ItemTarget(Items.BOW, 1));
                }
                // Arrows
                int arrows = mod.getItemStorage().getItemCountInventoryOnly(Items.ARROW);
                if (arrows < bowArrowsNeeded) {
                    setDebugState("Collecting arrows " + arrows + "/" + bowArrowsNeeded);
                    return new CollectItemTask(new ItemTarget(Items.ARROW, bowArrowsNeeded));
                }
                // Wool for cage trap — highest value trap option
                int wool = countWool(mod);
                if (wool < WOOL_NEEDED) {
                    Task cityWool = harvestNearbyWool(mod, null, WOOL_NEEDED);
                    if (cityWool != null) {
                        setDebugState("Harvesting city wool " + wool + "/" + WOOL_NEEDED);
                        return cityWool;
                    }
                    setDebugState("Collecting wool " + wool + "/" + WOOL_NEEDED);
                    return TaskCatalogue.getItemTask(Items.WHITE_WOOL, WOOL_NEEDED);
                }
                // Tower blocks — optional, only grab if convenient (small amount for short pillar)
                int buildBlocks = PlaceBlockTask.getMaterialCount(mod);
                if (buildBlocks < BUILD_BLOCKS_NEEDED && mod.getItemStorage().hasEmptyInventorySlot()) {
                    setDebugState("Collecting tower blocks " + buildBlocks + "/" + BUILD_BLOCKS_NEEDED);
                    return PlaceBlockTask.getMaterialTask(BUILD_BLOCKS_NEEDED);
                }
                // Iron blocks for golems — TaskCatalogue handles crafting from ingots via mass-craft
                int ironBlocks = mod.getItemStorage().getItemCountInventoryOnly(Items.IRON_BLOCK);
                int ironIngots = mod.getItemStorage().getItemCountInventoryOnly(Items.IRON_INGOT);
                int wantBlocks = golemCount * 4;
                if (ironBlocks < wantBlocks && (ironBlocks + ironIngots / 9) >= wantBlocks) {
                    setDebugState("Crafting iron blocks " + ironBlocks + "/" + wantBlocks);
                    return TaskCatalogue.getItemTask(Items.IRON_BLOCK, wantBlocks);
                }
                // Carved pumpkins — CarveThenCollectTask: get shears + raw pumpkins, carve in world
                int pumpkins = mod.getItemStorage().getItemCountInventoryOnly(Items.CARVED_PUMPKIN)
                             + mod.getItemStorage().getItemCountInventoryOnly(Items.JACK_O_LANTERN);
                if (pumpkins < golemCount && ironBlocks >= wantBlocks) {
                    setDebugState("Collecting carved pumpkins " + pumpkins + "/" + golemCount);
                    return TaskCatalogue.getItemTask("carved_pumpkin", golemCount);
                }
                phase = Phase.FIND_SHRIEKER;
                say("Gear ready. Heading to ancient city.");
            }

            // ── FIND_SHRIEKER ────────────────────────────────────────────────
            case FIND_SHRIEKER -> {
                BlockPos found = findShrieker(mod);
                if (found != null) {
                    shriekerPos = found;
                    int wool = countWool(mod);
                    if (wool < WOOL_NEEDED) {
                        Task cityWool = harvestNearbyWool(mod, shriekerPos, WOOL_NEEDED);
                        if (cityWool != null) {
                            setDebugState("Harvesting ancient city wool " + wool + "/" + WOOL_NEEDED);
                            return cityWool;
                        }
                    }
                    int possibleGolems = Math.max(availableGolems(mod), partialGolemFrames(mod, shriekerPos));
                    if (possibleGolems > 0) {
                        golemBuildTarget = Math.min(golemCount, possibleGolems);
                        phase = Phase.BUILD_GOLEMS;
                        say("Shrieker at " + shriekerPos.toShortString()
                                + " — building/resuming " + golemBuildTarget + " golem(s)");
                    } else {
                        phase = Phase.TRIGGER;
                        say("Shrieker at " + shriekerPos.toShortString() + " — triggering");
                    }
                    return null;
                }
                // Navigate to shrieker search range (wander within 64 blocks looking)
                setDebugState("Searching for sculk shrieker");
                plannedTower = null;
                // Stay put and scan; if none found nearby, advance anyway after timeout
                return null;
            }

            // ── BUILD_GOLEMS ─────────────────────────────────────────────────
            case BUILD_GOLEMS -> {
                // Craft iron blocks from ingots if needed
                int ironBlocks  = mod.getItemStorage().getItemCountInventoryOnly(Items.IRON_BLOCK);
                int ironIngots  = mod.getItemStorage().getItemCountInventoryOnly(Items.IRON_INGOT);
                int blocksNeeded = (golemsBuilt + 1) * 4;
                if (ironBlocks < 4 && ironIngots >= 9) {
                    int target = Math.min(blocksNeeded, ironBlocks + ironIngots / 9);
                    setDebugState("Crafting iron blocks from ingots " + ironBlocks + "/" + target);
                    return TaskCatalogue.getItemTask(Items.IRON_BLOCK, target);
                }
                if (golemBuildTarget <= 0) {
                    golemBuildTarget = Math.min(golemCount,
                            Math.max(availableGolems(mod), partialGolemFrames(mod, shriekerPos)));
                }

                if (golemsBuilt >= golemBuildTarget || golemsBuilt >= golemCount) {
                    phase = Phase.TRIGGER;
                    say("Golems placed. Triggering shrieker.");
                    return null;
                }

                // Place 5 blocks north of shrieker per golem, spaced 5 apart
                BlockPos origin = shriekerPos.north(6 + golemsBuilt * 5);
                Task repair = repairDamagedGolem(mod, origin);
                if (repair != null) return repair;
                if (hasIronGolemNear(mod, origin)) {
                    golemsBuilt++;
                    golemStep = 0;
                    setDebugState("Existing golem found; skipping finished frame");
                    return null;
                }
                if (golemStep == 4 && !hasFullIronFrame(mod, origin)) {
                    golemStep = firstMissingIronStep(mod, origin);
                    return null;
                }
                int[] off = GOLEM_OFFSETS[golemStep];
                BlockPos target = origin.offset(off[0], off[1], off[2]);

                if (isExpectedGolemBlock(mod.getWorld().getBlockState(target), golemStep)) {
                    advanceGolemStep(mod);
                    return null;
                }
                boolean isPumpkin = golemStep == 4;
                if (isPumpkin && !hasPumpkin(mod)) {
                    setDebugState("Collecting pumpkin to finish golem frame");
                    return TaskCatalogue.getItemTask("carved_pumpkin", 1);
                }
                Block block = isPumpkin ? Blocks.CARVED_PUMPKIN : Blocks.IRON_BLOCK;
                setDebugState("Golem " + (golemsBuilt + 1) + " block " + golemStep);
                return new PlaceBlockTask(target, block);
            }

            // ── TRIGGER ──────────────────────────────────────────────────────
            case TRIGGER -> {
                if (shriekerPos == null) { phase = Phase.FIND_SHRIEKER; return null; }
                // Get adjacent to shrieker, disable sneak for one tick to trigger it
                BlockPos standPos = shriekerPos.north(2);
                if (mod.getPlayer().blockPosition().distSqr(standPos) > 9) {
                    setDebugState("Moving to shrieker");
                    AncientCityHelper.setManualSneak(false);
                    return new GetToBlockTask(standPos);
                }
                // Disable sneak and sprint toward it — one vibration is enough
                AncientCityHelper.setManualSneak(false);
                BaritoneAPI.getSettings().allowSprint.value = true;
                triggerTimer = 0;
                phase = Phase.SPRINT_AND_DIG;
                say("Shrieker triggered! Sprinting to warden.");
            }

            // ── SPRINT_AND_DIG ───────────────────────────────────────────────
            case SPRINT_AND_DIG -> {
                triggerTimer++;

                // Wait for warden to appear (up to 5 seconds after trigger)
                if (warden == null) {
                    if (triggerTimer > 100) {
                        // No warden appeared — go back and try again
                        phase = Phase.TRIGGER;
                        say("No warden spawned. Retrying.");
                    }
                    setDebugState("Waiting for warden to spawn...");
                    return null;
                }

                // Warden found — record spawn position once
                if (wardenSpawn == null) {
                    wardenSpawn = warden.blockPosition();
                    digTargets = buildDigTargets(wardenSpawn);
                    digIndex = 0;
                    trapDug = false;
                    cageStep = 0;
                    BaritoneAPI.getSettings().allowSprint.value = true;
                    // Prefer wool cage (silences warden) over pit digging
                    if (countWool(mod) >= 4) {
                        phase = Phase.WOOL_CAGE;
                        say("Warden emerging at " + wardenSpawn.toShortString() + ". Building wool cage!");
                    } else {
                        say("Warden emerging at " + wardenSpawn.toShortString() + ". Digging trap.");
                    }
                    return null;
                }

                // If warden has finished emerging (animation no longer active), move on
                if (!warden.emergeAnimationState.isStarted() && triggerTimer > 60) {
                    if (!trapDug && wardenTargetingPlayer(mod, warden)) {
                        phase = Phase.RETRAP_RETREAT;
                        say("Trap failed and warden is on us. Repositioning.");
                    } else {
                        phase = Phase.RETREAT;
                        say("Dig window closed. Retreating.");
                    }
                    AncientCityHelper.setManualSneak(true);
                    return null;
                }

                // Check for blocked positions before committing
                for (BlockPos target : digTargets) {
                    if (isProtectedLootBlock(mod, target)) {
                        trapDug = false;
                        logFail("trap blocked: protected loot block at " + target.toShortString());
                        phase = Phase.RETRAP_RETREAT;
                        return null;
                    }
                    if (!isSafeTrapBlock(mod, target)) {
                        trapDug = false;
                        logFail("trap blocked: fluid/hazard at " + target.toShortString());
                        phase = Phase.RETRAP_RETREAT;
                        return null;
                    }
                }

                // Use clearArea to mine the exact bounding box — no type-based roaming
                boolean pitDone = digTargets.stream()
                        .allMatch(p -> mod.getWorld().getBlockState(p).isAir());
                if (!pitDone) {
                    // Corner0: top-layer offset(0,0,0), Corner1: bottom-layer offset(1,-DIG_DEPTH+1,1)
                    BlockPos c0 = wardenSpawn.offset(-DIG_RADIUS + 1, 0, -DIG_RADIUS + 1);
                    BlockPos c1 = wardenSpawn.offset(DIG_RADIUS, -(DIG_DEPTH - 1), DIG_RADIUS);
                    setDebugState("Clearing trap pit " + c0.toShortString() + " → " + c1.toShortString());
                    BaritoneAPI.getProvider().getPrimaryBaritone()
                            .getBuilderProcess().clearArea(c0, c1);
                    return null;
                }

                trapDug = true;
                // If we have wool and cage isn't done, do wool cage after pit
                if (countWool(mod) >= 4 && !woolCaged) {
                    phase = Phase.WOOL_CAGE;
                    say("Pit dug. Now placing wool cage.");
                } else {
                    phase = Phase.RETREAT;
                    say("Trap dug. Retreating.");
                    AncientCityHelper.setManualSneak(true);
                }
            }

            // ── WOOL_CAGE ─────────────────────────────────────────────────────
            // Place wool blocks around the warden during its emerge animation.
            // Wool absorbs vibrations — caged warden is deaf to player movement/attacks.
            case WOOL_CAGE -> {
                if (warden == null || wardenSpawn == null) { phase = Phase.RETREAT; return null; }

                // Emerge window closed — take whatever we placed and move on
                boolean stillEmerging = warden.emergeAnimationState.isStarted();
                if (!stillEmerging && triggerTimer > 60) {
                    woolCaged = cageStep >= CAGE_OFFSETS.length;
                    if (woolCaged) {
                        // Cage complete: warden is deaf — approach for melee or pit if not dug
                        if (!trapDug) {
                            phase = Phase.SPRINT_AND_DIG;
                            say("Wool cage complete! Digging pit under trapped warden.");
                        } else {
                            phase = Phase.FIGHT;
                            AncientCityHelper.setManualSneak(false);
                            say("Wool cage + pit complete! Warden is deaf and stuck.");
                        }
                    } else {
                        // Partial cage — still useful, retreat and fight
                        woolCaged = cageStep > 0;
                        phase = Phase.RETREAT;
                        say("Partial cage (" + cageStep + "/" + CAGE_OFFSETS.length + " blocks). Retreating.");
                        AncientCityHelper.setManualSneak(true);
                    }
                    return null;
                }

                // Out of wool — fall back to pit dig
                if (countWool(mod) == 0) {
                    phase = Phase.SPRINT_AND_DIG;
                    say("Out of wool during cage. Digging pit instead.");
                    return null;
                }

                if (cageStep >= CAGE_OFFSETS.length) {
                    woolCaged = true;
                    if (!trapDug) {
                        phase = Phase.SPRINT_AND_DIG;
                        say("Wool cage done! Digging pit while warden is still emerging.");
                    } else {
                        phase = Phase.FIGHT;
                        AncientCityHelper.setManualSneak(false);
                        say("Wool cage + pit complete! Warden is deaf and stuck.");
                    }
                    return null;
                }

                // Place next cage block
                int[] off = CAGE_OFFSETS[cageStep];
                BlockPos cagePos = wardenSpawn.offset(off[0], off[1], off[2]);

                // Skip if already solid (pre-existing block or we placed it)
                BlockState existing = mod.getWorld().getBlockState(cagePos);
                if (!existing.isAir()) {
                    cageStep++;
                    return null;
                }
                // Equip wool — any color works
                if (!equipAnyWool(mod)) {
                    phase = Phase.SPRINT_AND_DIG;
                    return null;
                }
                setDebugState("Cage block " + (cageStep + 1) + "/" + CAGE_OFFSETS.length
                        + " at " + cagePos.toShortString());
                return new PlaceBlockTask(cagePos, bestWoolBlock(mod));
            }

            // ── RETREAT ──────────────────────────────────────────────────────
            case RETRAP_RETREAT -> {
                releaseTowerInputs(mod);
                BowAimbot.setEnabled(false);
                AncientCityHelper.setManualSneak(false);
                if (warden != null && warden.isAlive() && wardenTargetingPlayer(mod, warden)) {
                    double dist = warden.distanceTo(mod.getPlayer());
                    if (dist < RETRAP_DIST) {
                        setDebugState("Trap failed; escaping " + (int) dist + "/" + RETRAP_DIST + "b");
                        return new GetToBlockTask(safeRetreatPos(mod, warden, RETRAP_DIST + 8));
                    }
                }
                retrapAttempts++;
                if (retrapAttempts > maxRetraps) {
                    if (shriekerPos != null) badShriekers.add(shriekerPos);
                    logFail("retrap cap hit; relocating shrieker");
                    shriekerPos = null;
                    warden = null;
                    phase = Phase.FIND_SHRIEKER;
                    return null;
                }
                warden = null;
                wardenSpawn = null;
                digTargets.clear();
                digIndex = 0;
                triggerTimer = 0;
                trapDug = false;
                towerBase = null;
                plannedTower = null;
                towerTimer = 0;
                phase = Phase.TRIGGER;
                say("Retrying trap.");
            }

            case RETREAT -> {
                if (warden == null) { say("Warden gone."); return null; }
                AncientCityHelper.setManualSneak(false);
                BaritoneAPI.getSettings().allowSprint.value = true;

                List<Warden> retreatFrom = allWardens(mod);
                if (retreatFrom.isEmpty()) retreatFrom = List.of(warden);
                // Distance from nearest warden (may not be the primary)
                double dist = retreatFrom.stream()
                        .mapToDouble(w -> w.distanceTo(mod.getPlayer())).min().orElse(0);
                double tunnelStartDist = retreatDist + UNDERMINE_START_EXTRA_DIST;
                if (lastRetreatDist >= 0.0D && Math.abs(dist - lastRetreatDist) < 0.25D) retreatStuckTicks++;
                else retreatStuckTicks = 0;
                lastRetreatDist = dist;

                boolean strictTarget = wardenHasPlayerTarget(mod, warden);
                boolean coveredNoTarget = !strictTarget
                        && dist >= retreatDist
                        && isPlayerCoveredFromWarden(mod, warden);
                boolean farEnough = dist >= tunnelStartDist - 2.0D;
                boolean stalledSafe = !strictTarget && dist >= retreatDist + 6.0D && retreatStuckTicks > 40;
                if (farEnough || coveredNoTarget || stalledSafe) {
                    phase = postRetreatPhase;
                    say("Safe at " + (int) dist + "b from " + retreatFrom.size() + " warden(s) — entering " + postRetreatPhase);
                    if (postRetreatPhase == Phase.FIGHT) {
                        equipBestRangedWeapon(mod);
                        BowAimbot.setForcedTarget(warden);
                        BowAimbot.setEnabled(hasRangedWeapon(mod));
                    }
                    return null;
                }
                BaritoneAPI.getSettings().allowSprint.value = true;
                BlockPos safePos = safeRetreatPosFromAll(mod, retreatFrom, (int) Math.ceil(tunnelStartDist));
                setDebugState("Retreating " + (int) dist + " / " + (int) tunnelStartDist + "b from "
                        + retreatFrom.size() + " warden(s)");
                return new GetToBlockTask(safePos);
            }

            // ── TOWER (opportunistic only) ────────────────────────────────────
            case TOWER -> {
                if (warden == null || !warden.isAlive()) {
                    releaseTowerInputs(mod);
                    say("Warden gone.");
                    return null;
                }
                double dist = warden.distanceTo(mod.getPlayer());
                if (dist < retreatDist - 2 && wardenTargetingPlayer(mod, warden)) {
                    releaseTowerInputs(mod);
                    phase = Phase.RETREAT;
                    return null;
                }
                if (towerBase == null) towerBase = mod.getPlayer().blockPosition();
                if (plannedTower != null && mod.getPlayer().blockPosition().distSqr(plannedTower) > 4) {
                    if (!isSafeStand(mod, plannedTower)) {
                        plannedTower = findTowerSpot(mod, warden);
                    }
                    if (plannedTower == null) {
                        // No safe tower spot — fight from ground
                        releaseTowerInputs(mod);
                        phase = Phase.FIGHT;
                        equipBestRangedWeapon(mod);
                        BowAimbot.setForcedTarget(warden);
                        BowAimbot.setEnabled(hasRangedWeapon(mod));
                        say("Tower spot gone; fighting from ground.");
                        return null;
                    }
                    setDebugState("Moving to tower spot");
                    return new GetToBlockTask(plannedTower);
                }
                int height = Math.max(0, mod.getPlayer().blockPosition().getY() - towerBase.getY());
                boolean clearShot = BowAimbot.hasSafeShot(Minecraft.getInstance(), mod.getPlayer(), warden);
                if (!hasPillarSupport(mod) || !hasHeadroom(mod, 3)) {
                    // Can't build here — fight from current height
                    releaseTowerInputs(mod);
                    phase = Phase.FIGHT;
                    equipBestRangedWeapon(mod);
                    BowAimbot.setForcedTarget(warden);
                    BowAimbot.setEnabled(hasRangedWeapon(mod));
                    say("Tower blocked. Fighting at height=" + height + ".");
                    return null;
                }
                if (height > lastTowerHeight) {
                    lastTowerHeight = height;
                    towerStallTicks = 0;
                } else if (++towerStallTicks > TOWER_STALL_TICKS) {
                    // Stalled — fight from whatever height we reached
                    releaseTowerInputs(mod);
                    phase = Phase.FIGHT;
                    equipBestRangedWeapon(mod);
                    BowAimbot.setForcedTarget(warden);
                    BowAimbot.setEnabled(hasRangedWeapon(mod));
                    logFail("tower stalled at " + height + "; fighting from here");
                    return null;
                }
                int buildBlocks = PlaceBlockTask.getMaterialCount(mod);
                if (buildBlocks <= 0 || height >= towerHeight || (height >= 3 && clearShot)) {
                    releaseTowerInputs(mod);
                    phase = Phase.FIGHT;
                    equipBestRangedWeapon(mod);
                    BowAimbot.setForcedTarget(warden);
                    BowAimbot.setEnabled(hasRangedWeapon(mod));
                    say("Tower ready/done. Height=" + height + " dist=" + (int) dist + "b weapon=" + bestWeaponName(mod));
                    return null;
                }
                if (!mod.getSlotHandler().forceEquipItem(PlaceBlockTask.getStructureMaterials())) {
                    // No blocks left — fight from here
                    releaseTowerInputs(mod);
                    phase = Phase.FIGHT;
                    equipBestRangedWeapon(mod);
                    BowAimbot.setForcedTarget(warden);
                    BowAimbot.setEnabled(hasRangedWeapon(mod));
                    return null;
                }
                setDebugState("Towering " + height + "/" + towerHeight + " LOS=" + clearShot);
                mod.getInputControls().hold(Input.JUMP);
                LookHelper.lookAt(mod, mod.getPlayer().blockPosition().below(), Direction.UP);
                if (++towerTimer % 4 == 0) {
                    placeUnderPlayer(mod);
                }
                return null;
            }

            case FIGHT -> {
                // With multiple wardens, always target the lowest-HP free one first
                List<Warden> freeWardens = allWardens(mod).stream()
                        .filter(w -> !isWardenInPit(mod, w)).toList();
                if (!freeWardens.isEmpty()) {
                    Warden lowestHp = freeWardens.stream()
                            .min(Comparator.comparingDouble(Warden::getHealth)).orElse(null);
                    if (lowestHp != null && lowestHp != warden) {
                        warden = lowestHp;
                        BowAimbot.setForcedTarget(warden);
                    }
                }
                if (warden == null || !warden.isAlive()) {
                    // Check if any warden at all is alive before declaring clear
                    if (allWardens(mod).isEmpty()) {
                        BowAimbot.setEnabled(false);
                        BowAimbot.clearForcedTarget();
                        phase = Phase.VERIFY_CLEAR;
                        missingWardenTicks = 0;
                        say("All wardens gone. Verifying clear.");
                    }
                    return null;
                }

                double dist = warden.distanceTo(mod.getPlayer());
                boolean inPitNow = isWardenInPit(mod, warden);
                // Latch pitConfirmed on: set when detected in pit, clear only after 10 consecutive ticks out.
                // This prevents one flickery tick from sending us sprinting away mid-melee.
                if (inPitNow) {
                    pitConfirmed = true;
                    pitEscapeTicks = 0;
                } else if (pitConfirmed) {
                    if (++pitEscapeTicks >= 10) {
                        pitConfirmed = false;
                        pitEscapeTicks = 0;
                    }
                }
                boolean inPit = pitConfirmed;
                boolean contained = inPit || woolCaged;
                AncientCityHelper.setManualSneak(!contained);

                // Back off only when free (not contained) and too close
                if (!contained && (dist < retreatDist - 2 || (wardenTargetingPlayer(mod, warden) && dist < retreatDist))) {
                    BowAimbot.setEnabled(false);
                    BlockPos safePos = safeRetreatPos(mod, warden, retreatDist + 8);
                    setDebugState("Too close (inPit=" + inPit + "); retreating hard");
                    return new GetToBlockTask(safePos);
                }

                // Warden contained (in pit or wool cage)
                if (contained) {
                    // With ranged weapon: actively hunt for a shooting position.
                    // The warden is immobile — we have all the time we need.
                    if (inPit && !woolCaged && hasRangedWeapon(mod)) {
                        // Already have clear shot from safe distance — shoot
                        if (dist >= SONIC_BOOM_SAFE_DIST
                                && BowAimbot.hasSafeShot(Minecraft.getInstance(), mod.getPlayer(), warden)) {
                            equipBestRangedWeapon(mod);
                            BowAimbot.setForcedTarget(warden);
                            BowAimbot.setEnabled(true);
                            setDebugState("Bow on pit-trapped warden dist=" + (int) dist + " HP=" + (int) warden.getHealth());
                            return null;
                        }
                        BowAimbot.setEnabled(false);
                        // Try a ground-level LOS position that is also outside shriek range
                        BlockPos shot = findShootingSpot(mod, warden);
                        if (shot != null) {
                            double shotDist = warden.position().distanceTo(Vec3.atCenterOf(shot));
                            if (shotDist >= SONIC_BOOM_SAFE_DIST) {
                                if (mod.getPlayer().blockPosition().distSqr(shot) > 4) {
                                    setDebugState("Moving to safe pit-LOS spot (dist=" + (int) shotDist + "b)");
                                    return new GetToBlockTask(shot);
                                }
                                // Already at the spot — fall through to tower
                            }
                        }
                        // No safe ground LOS — get blocks if needed, then tower from safe distance
                        int buildBlocks = PlaceBlockTask.getMaterialCount(mod);
                        if (buildBlocks < 3 && mod.getItemStorage().hasEmptyInventorySlot()) {
                            setDebugState("Fetching tower blocks for pit angle");
                            return PlaceBlockTask.getMaterialTask(8);
                        }
                        if (buildBlocks >= 3) {
                            // Pick a tower base ≥ SONIC_BOOM_SAFE_DIST from warden if we don't have one
                            if (towerBase == null || warden.position().distanceTo(Vec3.atCenterOf(towerBase)) < SONIC_BOOM_SAFE_DIST) {
                                towerBase = safeRetreatPos(mod, warden, (int) SONIC_BOOM_SAFE_DIST + 2);
                            }
                            if (mod.getPlayer().blockPosition().distSqr(towerBase) > 4) {
                                setDebugState("Moving to safe tower base for pit shot");
                                return new GetToBlockTask(towerBase);
                            }
                            int height = mod.getPlayer().blockPosition().getY() - towerBase.getY();
                            if (height < towerHeight && !BowAimbot.hasSafeShot(Minecraft.getInstance(), mod.getPlayer(), warden)) {
                                setDebugState("Towering for pit-shot angle h=" + height + " dist=" + (int) dist + "b");
                                if (mod.getSlotHandler().forceEquipItem(PlaceBlockTask.getStructureMaterials())) {
                                    mod.getInputControls().hold(Input.JUMP);
                                }
                                return null;
                            }
                        }
                        // Last resort: no blocks, no LOS — fall through to melee
                        // (warden is trapped; we accept the risk)
                    }
                    // Melee fallback — only when warden is nearly dead; otherwise wait for a shot angle
                    if (warden.getHealth() > FINISH_HP) {
                        setDebugState("No shot angle; circling for LOS (HP=" + (int) warden.getHealth() + ")");
                        BlockPos circle = safeRetreatPos(mod, warden, (int) SONIC_BOOM_SAFE_DIST - 2);
                        return new GetToBlockTask(circle);
                    }
                    BowAimbot.setEnabled(false);
                    equipBestMeleeWeapon(mod);
                    // Stand at surface level ADJACENT to the pit — Baritone mines/climbs/towers to reach it
                    int surfaceY = wardenSpawn != null ? wardenSpawn.getY() : warden.blockPosition().getY() + DIG_DEPTH;
                    BlockPos edge = new BlockPos(warden.blockPosition().getX(), surfaceY, warden.blockPosition().getZ() - 1);
                    if (mod.getPlayer().blockPosition().distSqr(edge) > 4) {
                        setDebugState("Moving to pit rim for melee");
                        return new GetToBlockTask(edge);
                    }
                    setDebugState((woolCaged ? "Cage" : "Pit") + "-melee HP=" + (int) warden.getHealth());
                    return new adris.altoclef.tasks.entity.KillEntityTask(warden);
                }

                // No ranged weapon and warden not contained — must lure into pit first
                if (!hasRangedWeapon(mod) && !contained) {
                    BowAimbot.setEnabled(false);
                    lureDigPos = null; lureDig.clear(); lureDigIdx = 0;
                    lureBaitTicks = 0; lureHoldTicks = 0;
                    phase = Phase.LURE_PIT;
                    say("No ranged weapon, warden free — luring into pit.");
                    return null;
                }
                // No ranged but warden IS contained — safe to melee
                if (!hasRangedWeapon(mod)) {
                    BowAimbot.setEnabled(false);
                    equipBestMeleeWeapon(mod);
                    setDebugState("No ranged, pit/cage — melee HP " + (int) warden.getHealth());
                    return new adris.altoclef.tasks.entity.KillEntityTask(warden);
                }

                BowAimbot.setForcedTarget(warden);
                equipBestRangedWeapon(mod);

                if (!BowAimbot.hasSafeShot(Minecraft.getInstance(), mod.getPlayer(), warden)) {
                    losFailBlock = shotBlocker(mod, warden);
                    // Only move toward clear shot if the shot position is actually safe to reach
                    BlockPos shot = findShootingSpot(mod, warden);
                    if (shot != null) {
                        double shotDist = warden.position().distanceTo(Vec3.atCenterOf(shot));
                        if (shotDist >= retreatDist && mod.getPlayer().blockPosition().distSqr(shot) > 9) {
                            BowAimbot.setEnabled(false);
                            setDebugState("Moving for clear shot (dist=" + (int) shotDist + "b from warden)");
                            return new GetToBlockTask(shot);
                        }
                    }
                    // No safe shot position — lure into pit
                    BowAimbot.setEnabled(false);
                    lureDigPos = null;
                    lureBaitPos = null;
                    lureHoldPos = null;
                    lureDig.clear();
                    lureDigIdx = 0;
                    lureBaitTicks = 0;
                    lureHoldTicks = 0;
                    phase = Phase.LURE_PIT;
                    say("No LOS, warden not trapped — luring into pit.");
                    return null;
                }
                losFailBlock = null;
                if (!BowAimbot.isEnabled()) BowAimbot.setEnabled(true);
                setDebugState("Ranged [" + bestWeaponName(mod) + "] HP=" + (int) warden.getHealth()
                        + " dist=" + (int) dist + "b caged=" + woolCaged + " pit=" + inPit);
            }

            // ── LURE_PIT ─────────────────────────────────────────────────────
            // Strategy: dig a narrow trench between us and the warden, then walk
            // close enough to generate aggro, then SPRINT back past the pit so the
            // warden chases us and falls in.
            //
            // Sequence:
            //   1. Find pit position (between player and warden line)
            //   2. Dig 2×1×2 trench
            //   3. BAIT: stop sneaking, walk toward warden until aggro (~18b)
            //   4. SPRINT: race back past the pit to hold position
            //   5. WAIT: warden chases through pit and falls in
            case LURE_PIT -> {
                if (warden == null || !warden.isAlive()) { phase = Phase.VERIFY_CLEAR; return null; }

                double lureDist = warden.distanceTo(mod.getPlayer());

                // Already trapped — fight immediately
                if (isWardenInPit(mod, warden)) {
                    say("Warden fell into pit!");
                    phase = Phase.RETREAT;
                    return null;
                }

                // ── Step 1: find pit ────────────────────────────────────────
                if (lureDigPos == null) {
                    lureDigPos = findLurePitPos(mod, warden);
                    if (lureDigPos == null) {
                        logFail("lure: no suitable pit location; giving up");
                        phase = Phase.FIGHT;
                        return null;
                    }
                    lureDig = buildLureTrenchTargets(lureDigPos);
                    lureDigIdx = 0;
                    lureBaitTicks = 0;
                    lureHoldTicks = 0;
                    // Direction from pit toward player (away from warden)
                    Vec3 toWarden = new Vec3(
                        warden.getX() - lureDigPos.getX(), 0,
                        warden.getZ() - lureDigPos.getZ()).normalize();
                    // Bait pos: on the warden's side of the pit, ~8b from pit
                    lureBaitPos = lureDigPos.offset(
                        (int) Math.round(toWarden.x * 8), 0,
                        (int) Math.round(toWarden.z * 8));
                    // Hold pos: 10b past the pit on player's side (32b from warden if pit is at 22b)
                    lureHoldPos = lureDigPos.offset(
                        -(int) Math.round(toWarden.x * 10), 0,
                        -(int) Math.round(toWarden.z * 10));
                    say("Lure pit at " + lureDigPos.toShortString()
                        + " | bait=" + lureBaitPos.toShortString()
                        + " | hold=" + lureHoldPos.toShortString());
                }

                // ── Step 2: dig pit (sneak while digging to stay quiet) ─────
                // Check for blocked positions before committing
                for (BlockPos target : lureDig) {
                    if (!isSafeTrapBlock(mod, target) || isProtectedLootBlock(mod, target)) {
                        logFail("lure pit blocked at " + target.toShortString() + "; resetting pit");
                        lureDigPos = null;
                        return null;
                    }
                }

                // Use clearArea on the exact 2×2×2 pit
                boolean lureDigDone = lureDig.stream()
                        .allMatch(p -> mod.getWorld().getBlockState(p).isAir());
                if (!lureDigDone) {
                    // During DIG only: abort if warden gets into sonic boom range (otherwise we can't escape)
                    if (lureDist < SONIC_BOOM_SAFE_DIST) {
                        returnToLureAfterRetreat = true;
                        phase = Phase.RETREAT;
                        say("Warden too close while digging (" + (int) lureDist + "b); retreating.");
                        return null;
                    }
                    AncientCityHelper.setManualSneak(true);
                    BlockPos c0 = lureDigPos;
                    BlockPos c1 = lureDigPos.offset(1, -1, 1);
                    setDebugState("Clearing lure trench " + c0.toShortString() + " → " + c1.toShortString());
                    BaritoneAPI.getProvider().getPrimaryBaritone()
                            .getBuilderProcess().clearArea(c0, c1);
                    return null;
                }

                // ── Step 3: BAIT — approach warden to generate aggro ────────
                // Stop sneaking so footsteps register as vibrations.
                // Walk toward warden until it targets us OR we've been close long enough.
                boolean hasAggro = wardenTargetingPlayer(mod, warden);
                if (!hasAggro && lureBaitTicks < 80) {
                    AncientCityHelper.setManualSneak(false);
                    BaritoneAPI.getSettings().allowSprint.value = false; // walk (not sprint) = louder vibrations
                    if (lureBaitPos != null && mod.getPlayer().blockPosition().distSqr(lureBaitPos) > 4) {
                        setDebugState("BAIT: walking toward warden to generate aggro (" + (int) lureDist + "b)");
                        return new GetToBlockTask(lureBaitPos);
                    }
                    lureBaitTicks++;
                    setDebugState("BAIT: stomping for aggro... " + lureBaitTicks + "t anger=" + warden.getClientAngerLevel());
                    // Jump every 10 ticks — jumping is a vibration the warden detects
                    if (lureBaitTicks % 10 == 0) {
                        mod.getInputControls().hold(Input.JUMP);
                    } else {
                        mod.getInputControls().release(Input.JUMP);
                    }
                    return null;
                }
                mod.getInputControls().release(Input.JUMP);

                // ── Step 4: SPRINT past the pit to hold position ─────────────
                AncientCityHelper.setManualSneak(false);
                BaritoneAPI.getSettings().allowSprint.value = true;
                if (lureHoldPos != null && mod.getPlayer().blockPosition().distSqr(lureHoldPos) > 4) {
                    setDebugState("SPRINT: racing past pit to hold position");
                    return new GetToBlockTask(lureHoldPos);
                }

                // ── Step 5: HOLD — warden chases us and falls in ─────────────
                lureHoldTicks++;
                setDebugState("HOLD: waiting for warden to fall... " + lureHoldTicks + "t dist=" + (int) lureDist);
                if (lureHoldTicks > 160) {
                    // Warden didn't fall — reset and try with a new pit location or new bait
                    logFail("lure hold timeout; resetting bait cycle");
                    lureDigPos = null;
                    lureBaitPos = null;
                    lureHoldPos = null;
                    lureDig.clear();
                    lureDigIdx = 0;
                    lureBaitTicks = 0;
                    lureHoldTicks = 0;
                }
            }

            case UNDERMINE -> {
                if (warden == null || !warden.isAlive()) {
                    if (undermineTarget == null || missingWardenTicks > VERIFY_CLEAR_TICKS) {
                        phase = Phase.VERIFY_CLEAR;
                        resetUndermineState();
                        return null;
                    }
                }
                BowAimbot.setEnabled(false);
                AncientCityHelper.setManualSneak(false);
                BaritoneAPI.getSettings().allowSprint.value = true;
                if (undermineClearCooldown > 0) undermineClearCooldown--;

                BlockPos liveFeet = warden != null && warden.isAlive() ? warden.blockPosition() : undermineTarget;
                BlockPos playerPos = mod.getPlayer().blockPosition();
                if (warden != null && warden.isAlive()) {
                    double dist = warden.distanceTo(mod.getPlayer());
                    boolean strictTarget = wardenHasPlayerTarget(mod, warden);
                    boolean coveredNoTarget = !strictTarget && isPlayerCoveredFromWarden(mod, warden);
                    boolean closeThreat = !coveredNoTarget
                            && (dist < retreatDist || (wardenTargetingPlayer(mod, warden) && dist < retreatDist + 6.0D));
                    boolean alreadyUnderAttackColumn = undermineTarget != null
                            && horizontalDist(playerPos, undermineTarget) <= UNDERMINE_ATTACK_DRIFT
                            && playerPos.getY() <= undermineTarget.getY() - UNDERMINE_ATTACK_DEPTH;
                    if (closeThreat && !alreadyUnderAttackColumn) {
                        resetUndermineState();
                        phase = Phase.RETREAT;
                        setDebugState("UNDERMINE unsafe (" + (int) dist + "b); retreat first");
                        return null;
                    }
                }
                if (undermineTarget == null) {
                    setUndermineTarget(mod, liveFeet, "lock");
                } else {
                    int drift = horizontalDist(undermineTarget, liveFeet);
                    if (drift > UNDERMINE_ATTACK_DRIFT || liveFeet.getY() != undermineTarget.getY()) {
                        setUndermineTarget(mod, liveFeet, "retarget drift=" + drift);
                    }
                }

                BlockPos targetFeet = undermineTarget == null ? liveFeet : undermineTarget;
                int tunnelY = targetFeet.getY() - UNDERMINE_TUNNEL_DEPTH;
                int attackY = targetFeet.getY() - UNDERMINE_ATTACK_DEPTH;

                int liveHDist = horizontalDist(playerPos, liveFeet);
                boolean inPosition = liveHDist <= UNDERMINE_ATTACK_DRIFT
                        && playerPos.getY() == liveFeet.getY() - UNDERMINE_ATTACK_DEPTH;
                if (inPosition && warden != null && warden.isAlive()) {
                    KillAura.equipWeapon(mod);
                    mod.getKillAura().applyAura(warden);
                    setDebugState("UNDERMINE: swinging through floor (hp=" + (int) warden.getHealth() + ")");
                    return null;
                }

                // Step 1: clearArea shaft straight down at current X/Z to tunnelY
                boolean shaftDone = playerPos.getY() <= tunnelY;
                if (!shaftDone) {
                    BlockPos shaftTop = new BlockPos(playerPos.getX(), playerPos.getY() - 1, playerPos.getZ());
                    BlockPos shaftBot = new BlockPos(playerPos.getX(), tunnelY, playerPos.getZ());
                    if (isRegionClear(mod, shaftBot, shaftTop)) {
                        setDebugState("UNDERMINE: descending shaft to Y=" + tunnelY + " target=" + targetFeet.toShortString());
                        return new GetToBlockTask(shaftBot);
                    }
                    setDebugState("UNDERMINE: deep shaft to Y=" + tunnelY + " target=" + targetFeet.toShortString());
                    return new ClearRegionTask(shaftBot, shaftTop);
                }

                // Step 2: clear a 1-wide L tunnel at tunnelY. Do not clear the whole X/Z rectangle.
                BlockPos corner = new BlockPos(targetFeet.getX(), tunnelY, playerPos.getZ());
                BlockPos xLegA = new BlockPos(playerPos.getX(), tunnelY, playerPos.getZ());
                BlockPos xLegB = new BlockPos(targetFeet.getX(), tunnelY + 1, playerPos.getZ());
                if (!isRegionClear(mod, xLegA, xLegB)) {
                    setDebugState("UNDERMINE: X tunnel to " + corner.toShortString()
                            + " target=" + targetFeet.toShortString());
                    return new ClearRegionTask(xLegA, xLegB);
                }
                if (horizontalDist(playerPos, corner) > 0) {
                    setDebugState("UNDERMINE: moving to tunnel corner " + corner.toShortString());
                    return new GetToBlockTask(corner);
                }

                BlockPos zLegA = new BlockPos(targetFeet.getX(), tunnelY, playerPos.getZ());
                BlockPos zLegB = new BlockPos(targetFeet.getX(), tunnelY + 1, targetFeet.getZ());
                if (!isRegionClear(mod, zLegA, zLegB)) {
                    setDebugState("UNDERMINE: Z tunnel to " + targetFeet.toShortString()
                            + " live=" + liveFeet.toShortString());
                    return new ClearRegionTask(zLegA, zLegB);
                }

                // Tunnel clear — walk to attack position (GetToBlockTask safe now, all underground)
                BlockPos upA = new BlockPos(targetFeet.getX(), tunnelY, targetFeet.getZ());
                BlockPos upB = new BlockPos(targetFeet.getX(), attackY + 1, targetFeet.getZ());
                if (!isRegionClear(mod, upA, upB)) {
                    setDebugState("UNDERMINE: up-shaft under warden to Y=" + attackY
                            + " target=" + targetFeet.toShortString());
                    return new ClearRegionTask(upA, upB);
                }

                BlockPos attackPos = new BlockPos(targetFeet.getX(), attackY, targetFeet.getZ());
                setDebugState("UNDERMINE: moving to attack pos " + attackPos.toShortString()
                        + " live=" + liveFeet.toShortString());
                return new GetToBlockTask(attackPos);
            }

            case VERIFY_CLEAR -> {
                BowAimbot.setEnabled(false);
                BowAimbot.clearForcedTarget();
                Warden found = nearestWarden(mod);
                if (found != null) {
                    warden = found;
                    // Reset all pit/lure state — fresh fight on the new warden
                    pitConfirmed = false;
                    pitEscapeTicks = 0;
                    woolCaged = false;
                    lureDigPos = null;
                    lureDig.clear();
                    lureDigIdx = 0;
                    lureBaitPos = null;
                    lureHoldPos = null;
                    lureBaitTicks = 0;
                    lureHoldTicks = 0;
                    returnToLureAfterRetreat = false;
                    phase = Phase.LURE_PIT;
                    say("New warden detected — luring into pit.");
                    return null;
                }
                if (++missingWardenTicks >= VERIFY_CLEAR_TICKS) {
                    complete = true;
                    say("Warden clear.");
                    return null;
                }
                setDebugState("Verifying clear " + missingWardenTicks + "/" + VERIFY_CLEAR_TICKS);
            }
        }
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        isActive = false;
        BowAimbot.setEnabled(false);
        BowAimbot.clearForcedTarget();
        AncientCityHelper.setManualSneak(false);
        releaseTowerInputs(mod);
        try { BaritoneAPI.getSettings().allowSprint.value = true; } catch (Throwable ignored) {}
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return complete;
    }

    @Override
    protected boolean isEqual(Task other) { return other instanceof WardenTrapTask; }

    @Override
    protected String toDebugString() { return "WardenTrap(" + phase + ")"; }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void tickBookkeeping(AltoClef mod) {
        if (lastPhase != phase) {
            Debug.logInternal("[WardenTrap] phase " + (lastPhase == null ? "START" : lastPhase) + " -> " + phase);
            lastPhase = phase;
            phaseTicks = 0;
        } else {
            phaseTicks++;
        }
        if (phaseTicks > PHASE_STALL_TICKS) {
            if (phase == Phase.TOWER || phase == Phase.RETREAT || phase == Phase.SPRINT_AND_DIG) {
                logFail("phase stalled: " + phase + " " + phaseTicks + "t");
                phase = Phase.RETRAP_RETREAT;
                phaseTicks = 0;
            } else if (phaseTicks % PHASE_STALL_TICKS == 0) {
                Debug.logInternal("[WardenTrap] phase still active: " + phase + " " + phaseTicks + "t");
            }
        }
        if (golemRepairCooldown > 0) golemRepairCooldown--;
    }

    private Task handleOverride(AltoClef mod) {
        OverrideCommand command = pendingOverride;
        if (command == OverrideCommand.NONE) return null;
        pendingOverride = OverrideCommand.NONE;
        if (command == OverrideCommand.RETRAP) {
            logFail("manual retrap");
            phase = Phase.RETRAP_RETREAT;
            return null;
        }
        if (command == OverrideCommand.TOWER) {
            if (warden == null || !warden.isAlive()) {
                logFail("manual tower ignored: no warden");
                return null;
            }
            releaseTowerInputs(mod);
            plannedTower = findTowerSpot(mod, warden);
            towerBase = plannedTower != null ? plannedTower : mod.getPlayer().blockPosition();
            lastTowerHeight = 0;
            towerStallTicks = 0;
            phase = Phase.TOWER;
            return null;
        }
        if (command == OverrideCommand.FIGHT) {
            if (warden == null || !warden.isAlive()) {
                logFail("manual fight ignored: no warden");
                return null;
            }
            releaseTowerInputs(mod);
            BowAimbot.setForcedTarget(warden);
            phase = Phase.FIGHT;
        }
        return null;
    }

    private void publishStatus(AltoClef mod) {
        if (++statusTicks % 10 != 0 || mod.getPlayer() == null) return;
        double dist = warden == null ? -1 : warden.distanceTo(mod.getPlayer());
        int hp = warden == null ? -1 : (int) warden.getHealth();
        String target = wardenTargetName(mod, warden);
        int height = towerBase == null ? 0 : Math.max(0, mod.getPlayer().blockPosition().getY() - towerBase.getY());
        long dug = mod.getWorld() == null ? 0 : digTargets.stream().filter(p -> mod.getWorld().getBlockState(p).isAir()).count();
        int trapPct = digTargets.isEmpty() ? 0 : (int) (100.0D * dug / digTargets.size());
        boolean safeShot = warden != null && BowAimbot.hasSafeShot(Minecraft.getInstance(), mod.getPlayer(), warden);
        boolean shriek = warden != null && isSonicBoomWinding(warden);
        boolean meleeSwing = warden != null && isWardenMeleeSwinging(warden);
        lastStatus = "WardenTrap: " + phase
                + (shriek ? " ⚡SHRIEK" : "") + (meleeSwing ? " ✊SWING" : "")
                + " hp=" + hp
                + " dist=" + (dist < 0 ? "?" : (int) dist)
                + " shot=" + safeShot
                + " target=" + target
                + " tower=" + height + "/" + towerHeight
                + " trap=" + trapPct + "%"
                + " retrap=" + retrapAttempts + "/" + maxRetraps
                + " fail=" + lastFailReason;
        mod.getPlayer().displayClientMessage(Component.literal(lastStatus), true);
    }

    private void renderMarkers(AltoClef mod) {
        if (++particleTicks % 10 != 0 || mod.getWorld() == null) return;
        particle(mod, shriekerPos, ParticleTypes.SOUL_FIRE_FLAME);
        if (!digTargets.isEmpty()) {
            for (BlockPos p : digTargets) particle(mod, p, ParticleTypes.DAMAGE_INDICATOR);
        }
        particle(mod, plannedTower != null ? plannedTower : towerBase, ParticleTypes.HAPPY_VILLAGER);
        particle(mod, lureDigPos, ParticleTypes.SOUL);
        particle(mod, lureHoldPos, ParticleTypes.COMPOSTER);
        particle(mod, losFailBlock, ParticleTypes.END_ROD);
    }

    private static void particle(AltoClef mod, BlockPos pos, net.minecraft.core.particles.ParticleOptions type) {
        if (pos == null || mod.getWorld() == null) return;
        try {
            mod.getWorld().addParticle(type, pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D,
                    0.0D, 0.02D, 0.0D);
        } catch (Throwable ignored) {}
    }

    private void logFail(String reason) {
        lastFailReason = reason;
        Debug.logInternal("[WardenTrap] " + reason);
    }

    private void resetUndermineState() {
        undermineTarget = null;
        undermineLastClearA = null;
        undermineLastClearB = null;
        undermineClearCooldown = 0;
    }

    private void startUndermine(AltoClef mod, BlockPos target, String reason) {
        returnToLureAfterRetreat = false;
        resetUndermineState();
        retreatStuckTicks = 0;
        lastRetreatDist = -1.0D;
        phase = Phase.UNDERMINE;
        setUndermineTarget(mod, target, "start " + reason);
    }

    private void setUndermineTarget(AltoClef mod, BlockPos target, String reason) {
        undermineTarget = target;
        undermineLastClearA = null;
        undermineLastClearB = null;
        undermineClearCooldown = 0;
        try {
            mod.getClientBaritone().getPathingBehavior().forceCancel();
        } catch (Throwable ignored) {}
        logFail("undermine " + reason + " target=" + target.toShortString());
    }

    private void clearAreaThrottled(AltoClef mod, BlockPos a, BlockPos b) {
        if (undermineClearCooldown <= 0 || !sameRegion(a, b, undermineLastClearA, undermineLastClearB)) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().clearArea(a, b);
            undermineLastClearA = a;
            undermineLastClearB = b;
            undermineClearCooldown = UNDERMINE_CLEAR_COOLDOWN;
        }
    }

    private static boolean sameRegion(BlockPos a, BlockPos b, BlockPos c, BlockPos d) {
        if (a == null || b == null || c == null || d == null) return false;
        return (a.equals(c) && b.equals(d)) || (a.equals(d) && b.equals(c));
    }

    private static int horizontalDist(BlockPos a, BlockPos b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    /** How many golems we can build right now (iron blocks + ingots/9, capped by pumpkins). */
    private static int availableGolems(AltoClef mod) {
        int ironBlocks  = mod.getItemStorage().getItemCountInventoryOnly(Items.IRON_BLOCK);
        int ironIngots  = mod.getItemStorage().getItemCountInventoryOnly(Items.IRON_INGOT);
        int totalBlocks = ironBlocks + ironIngots / 9;
        int pumpkins    = mod.getItemStorage().getItemCountInventoryOnly(Items.CARVED_PUMPKIN)
                        + mod.getItemStorage().getItemCountInventoryOnly(Items.JACK_O_LANTERN);
        if (pumpkins == 0) return 0; // no pumpkins → skip golems entirely
        return Math.min(totalBlocks / 4, pumpkins);
    }

    /** 2×2 footprint, DIG_DEPTH deep, centred on the warden's block position. */
    private static List<BlockPos> buildDigTargets(BlockPos centre) {
        List<BlockPos> targets = new ArrayList<>();
        for (int y = 0; y > -DIG_DEPTH; y--) {
            for (int x = -DIG_RADIUS + 1; x <= DIG_RADIUS; x++) {
                for (int z = -DIG_RADIUS + 1; z <= DIG_RADIUS; z++) {
                    targets.add(centre.offset(x, y, z));
                }
            }
        }
        return targets;
    }

    private BlockPos findShrieker(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return null;
        BlockPos centre = mod.getPlayer().blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = -48; x <= 48; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -48; z <= 48; z++) {
                    BlockPos p = centre.offset(x, y, z);
                    if (isBadShrieker(p)) continue;
                    if (mod.getWorld().getBlockState(p).getBlock() == Blocks.SCULK_SHRIEKER) {
                        double d = p.distSqr(centre);
                        if (d < bestDist) { best = p; bestDist = d; }
                    }
                }
            }
        }
        return best;
    }

    private static BlockPos nearestShriekerLoose(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return null;
        BlockPos centre = mod.getPlayer().blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = -48; x <= 48; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -48; z <= 48; z++) {
                    BlockPos p = centre.offset(x, y, z);
                    if (mod.getWorld().getBlockState(p).getBlock() == Blocks.SCULK_SHRIEKER) {
                        double d = p.distSqr(centre);
                        if (d < bestDist) { best = p; bestDist = d; }
                    }
                }
            }
        }
        return best;
    }

    private List<Warden> allWardens(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return List.of();
        return mod.getWorld().getEntitiesOfClass(Warden.class,
                mod.getPlayer().getBoundingBox().inflate(60),
                w -> w.isAlive());
    }

    private Warden nearestWarden(AltoClef mod) {
        return allWardens(mod).stream()
                .min(Comparator.comparingDouble(w -> w.distanceTo(mod.getPlayer())))
                .orElse(null);
    }

    private void advanceGolemStep(AltoClef mod) {
        golemStep++;
        if (golemStep >= GOLEM_OFFSETS.length) {
            golemStep = 0;
            golemsBuilt++;
        }
    }

    private static boolean hasPumpkin(AltoClef mod) {
        return mod.getItemStorage().hasItemInventoryOnly(Items.CARVED_PUMPKIN, Items.JACK_O_LANTERN);
    }

    private static int partialGolemFrames(AltoClef mod, BlockPos shrieker) {
        if (shrieker == null || mod.getWorld() == null) return 0;
        int count = 0;
        for (int i = 0; i < golemCount; i++) {
            BlockPos origin = shrieker.north(6 + i * 5);
            if (hasAnyGolemFrameBlock(mod, origin)) count++;
        }
        return count;
    }

    private static boolean hasAnyGolemFrameBlock(AltoClef mod, BlockPos origin) {
        for (int[] off : GOLEM_OFFSETS) {
            BlockState state = mod.getWorld().getBlockState(origin.offset(off[0], off[1], off[2]));
            Block block = state.getBlock();
            if (block == Blocks.IRON_BLOCK || block == Blocks.CARVED_PUMPKIN || block == Blocks.JACK_O_LANTERN) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFullIronFrame(AltoClef mod, BlockPos origin) {
        for (int i = 0; i < 4; i++) {
            int[] off = GOLEM_OFFSETS[i];
            if (mod.getWorld().getBlockState(origin.offset(off[0], off[1], off[2])).getBlock() != Blocks.IRON_BLOCK) {
                return false;
            }
        }
        return true;
    }

    private static int firstMissingIronStep(AltoClef mod, BlockPos origin) {
        for (int i = 0; i < 4; i++) {
            int[] off = GOLEM_OFFSETS[i];
            if (mod.getWorld().getBlockState(origin.offset(off[0], off[1], off[2])).getBlock() != Blocks.IRON_BLOCK) {
                return i;
            }
        }
        return 4;
    }

    private static boolean isExpectedGolemBlock(BlockState state, int step) {
        Block block = state.getBlock();
        if (step < 4) return block == Blocks.IRON_BLOCK;
        return block == Blocks.CARVED_PUMPKIN || block == Blocks.JACK_O_LANTERN;
    }

    private static boolean hasIronGolemNear(AltoClef mod, BlockPos origin) {
        if (mod.getWorld() == null) return false;
        return !mod.getWorld().getEntitiesOfClass(IronGolem.class,
                new net.minecraft.world.phys.AABB(origin).inflate(8.0D),
                IronGolem::isAlive).isEmpty();
    }

    private Task repairDamagedGolem(AltoClef mod, BlockPos origin) {
        if (mod.getWorld() == null || !mod.getItemStorage().hasItemInventoryOnly(Items.IRON_INGOT)) return null;
        IronGolem golem = mod.getWorld().getEntitiesOfClass(IronGolem.class,
                new AABB(origin).inflate(8.0D),
                g -> g.isAlive() && g.getHealth() < g.getMaxHealth() - 4.0F)
                .stream()
                .min(Comparator.comparingDouble(g -> g.distanceToSqr(origin.getX(), origin.getY(), origin.getZ())))
                .orElse(null);
        if (golem == null) return null;
        if (mod.getPlayer().distanceToSqr(golem) > 9.0D) {
            setDebugState("Moving to repair golem");
            return new GetToBlockTask(golem.blockPosition());
        }
        if (golemRepairCooldown <= 0 && mod.getSlotHandler().forceEquipItem(Items.IRON_INGOT)) {
            LookHelper.lookAt(mod, golem.getEyePosition());
            mod.getController().interact(mod.getPlayer(), golem, InteractionHand.MAIN_HAND);
            golemRepairCooldown = 10;
            setDebugState("Repairing golem HP " + (int) golem.getHealth());
        }
        return null;
    }

    private static boolean wardenTargetingPlayer(AltoClef mod, Warden warden) {
        if (mod.getPlayer() == null || warden == null) return false;
        Entity target = warden.getTarget();
        if (target != null && target.equals(mod.getPlayer())) return true;
        return target == null
                && warden.getClientAngerLevel() >= 60
                && warden.distanceTo(mod.getPlayer()) < retreatDist + 8
                && !isPlayerCoveredFromWarden(mod, warden);
    }

    private static boolean wardenHasPlayerTarget(AltoClef mod, Warden warden) {
        if (mod.getPlayer() == null || warden == null) return false;
        Entity target = warden.getTarget();
        return target != null && target.equals(mod.getPlayer());
    }

    private static boolean isPlayerCoveredFromWarden(AltoClef mod, Warden warden) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return false;
        BlockPos playerPos = mod.getPlayer().blockPosition();
        for (int i = 1; i <= 3; i++) {
            BlockPos cover = playerPos.above(i);
            BlockState state = mod.getWorld().getBlockState(cover);
            if (!state.isAir() && (state.blocksMotion() || state.isSolid())) return true;
        }
        if (warden == null) return false;
        Vec3 start = mod.getPlayer().getEyePosition();
        Vec3 end = warden.getEyePosition();
        net.minecraft.world.phys.HitResult hit = mod.getWorld().clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mod.getPlayer()));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
    }

    private static String wardenTargetName(AltoClef mod, Warden warden) {
        if (warden == null) return "none";
        Entity target = warden.getTarget();
        if (target == null) return warden.getClientAngerLevel() >= 60 ? "angry" : "none";
        if (mod.getPlayer() != null && target.equals(mod.getPlayer())) return "player";
        if (target instanceof IronGolem) return "golem";
        return target.getType().toString();
    }

    private boolean isBadShrieker(BlockPos pos) {
        for (BlockPos bad : badShriekers) {
            if (bad.distSqr(pos) <= 36) return true;
        }
        return false;
    }

    private static boolean isProtectedLootBlock(AltoClef mod, BlockPos pos) {
        if (mod.getWorld() == null) return false;
        Block block = mod.getWorld().getBlockState(pos).getBlock();
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return path.contains("chest") || path.contains("barrel") || path.contains("shulker")
                || path.contains("crate") || path.contains("cache") || path.contains("sack");
    }

    private static boolean isSafeTrapBlock(AltoClef mod, BlockPos pos) {
        if (mod.getWorld() == null) return false;
        BlockState state = mod.getWorld().getBlockState(pos);
        return state.getFluidState().isEmpty() && !isHazardBlock(state.getBlock());
    }

    private static boolean isSafeStand(AltoClef mod, BlockPos pos) {
        if (mod.getWorld() == null) return false;
        BlockState feet = mod.getWorld().getBlockState(pos);
        BlockState head = mod.getWorld().getBlockState(pos.above());
        BlockState floor = mod.getWorld().getBlockState(pos.below());
        return feet.isAir()
                && head.isAir()
                && floor.getFluidState().isEmpty()
                && !isHazardBlock(floor.getBlock())
                && !isProtectedLootBlock(mod, pos.below())
                && floor.isFaceSturdy(mod.getWorld(), pos.below(), Direction.UP);
    }

    private static boolean isHazardBlock(Block block) {
        return block == Blocks.LAVA || block == Blocks.FIRE || block == Blocks.SOUL_FIRE
                || block == Blocks.MAGMA_BLOCK || block == Blocks.CACTUS
                || block == Blocks.POWDER_SNOW || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE;
    }

    private static boolean hasPillarSupport(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return false;
        BlockPos support = mod.getPlayer().blockPosition().below();
        BlockState state = mod.getWorld().getBlockState(support);
        return state.getFluidState().isEmpty()
                && !isProtectedLootBlock(mod, support)
                && !isHazardBlock(state.getBlock())
                && state.isFaceSturdy(mod.getWorld(), support, Direction.UP);
    }

    private static boolean hasHeadroom(AltoClef mod, int blocks) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return false;
        BlockPos pos = mod.getPlayer().blockPosition();
        for (int i = 1; i <= blocks; i++) {
            if (!mod.getWorld().getBlockState(pos.above(i)).isAir()) return false;
        }
        return true;
    }

    private static boolean hasHeadroomAt(AltoClef mod, BlockPos pos, int blocks) {
        if (mod.getWorld() == null) return false;
        for (int i = 1; i <= blocks; i++) {
            if (!mod.getWorld().getBlockState(pos.above(i)).isAir()) return false;
        }
        return true;
    }

    private static BlockPos findTowerSpot(AltoClef mod, Warden warden) {
        if (mod.getPlayer() == null || mod.getWorld() == null || warden == null) return null;
        BlockPos base = warden.blockPosition();
        BlockPos player = mod.getPlayer().blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int radius : new int[]{retreatDist + 2, retreatDist + 6, retreatDist + 10, retreatDist + 14}) {
            for (int a = 0; a < 360; a += 18) {
                double rad = Math.toRadians(a);
                int x = base.getX() + (int) Math.round(Math.cos(rad) * radius);
                int z = base.getZ() + (int) Math.round(Math.sin(rad) * radius);
                for (int y = player.getY() - 4; y <= player.getY() + 5; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!isSafeStand(mod, p)) continue;
                    if (!hasHeadroomAt(mod, p, towerHeight + 2)) continue;
                    double d = p.distSqr(player);
                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    private static BlockPos safeRetreatPos(AltoClef mod, Warden warden, int dist) {
        return safeRetreatPosFromAll(mod, List.of(warden), dist);
    }

    /** Finds a position at least {@code dist} blocks from every warden in the list. */
    private static BlockPos safeRetreatPosFromAll(AltoClef mod, List<Warden> wardens, int dist) {
        if (wardens.isEmpty()) return mod.getPlayer().blockPosition();
        // Compute centroid of all wardens as the "away from" origin
        double cx = wardens.stream().mapToDouble(w -> w.blockPosition().getX()).average().orElse(0);
        double cz = wardens.stream().mapToDouble(w -> w.blockPosition().getZ()).average().orElse(0);
        int cy = wardens.get(0).blockPosition().getY();
        BlockPos centroid = new BlockPos((int) cx, cy, (int) cz);
        BlockPos player = mod.getPlayer().blockPosition();
        BlockPos raw = awayFrom(centroid, player, dist);
        if (isSafeStand(mod, raw) && minDistToAll(raw, wardens) >= dist) return raw;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int radius : new int[]{dist, dist + 4, dist + 8, dist + 12}) {
            for (int a = 0; a < 360; a += 15) {
                double rad = Math.toRadians(a);
                int x = centroid.getX() + (int) Math.round(Math.cos(rad) * radius);
                int z = centroid.getZ() + (int) Math.round(Math.sin(rad) * radius);
                for (int y = player.getY() - 4; y <= player.getY() + 5; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!isSafeStand(mod, p)) continue;
                    if (minDistToAll(p, wardens) < dist - 4) continue; // must be safe from ALL
                    double d = p.distSqr(raw);
                    if (d < bestScore) {
                        bestScore = d;
                        best = p;
                    }
                }
            }
        }
        return best == null ? raw : best;
    }

    private static double minDistToAll(BlockPos pos, List<Warden> wardens) {
        return wardens.stream().mapToDouble(w -> Math.sqrt(w.blockPosition().distSqr(pos))).min().orElse(Double.MAX_VALUE);
    }

    private static BlockPos shotBlocker(AltoClef mod, Warden warden) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mod.getPlayer() == null || warden == null) return null;
        Vec3 start = mod.getPlayer().getEyePosition();
        Vec3 end = warden.getEyePosition();
        net.minecraft.world.phys.HitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mod.getPlayer()));
        if (hit instanceof BlockHitResult bhr) return bhr.getBlockPos();
        return null;
    }

    private static void placeUnderPlayer(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getController() == null || mod.getWorld() == null) return;
        BlockPos support = mod.getPlayer().blockPosition().below();
        if (!hasPillarSupport(mod)) return;
        mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
    }

    private static void releaseTowerInputs(AltoClef mod) {
        if (mod == null) return;
        mod.getInputControls().release(Input.JUMP);
        mod.getInputControls().release(Input.CLICK_RIGHT);
    }

    private static BlockPos findShootingSpot(AltoClef mod, Warden warden) {
        Minecraft mc = Minecraft.getInstance();
        if (mod.getPlayer() == null || mod.getWorld() == null || mc.level == null) return null;
        BlockPos base = warden.blockPosition();
        BlockPos playerPos = mod.getPlayer().blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int radius : new int[]{retreatDist + 2, retreatDist + 5, retreatDist + 8}) {
            for (int a = 0; a < 360; a += 22) {
                double rad = Math.toRadians(a);
                int x = base.getX() + (int) Math.round(Math.cos(rad) * radius);
                int z = base.getZ() + (int) Math.round(Math.sin(rad) * radius);
                for (int y = playerPos.getY() - 3; y <= playerPos.getY() + 5; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!isSafeStand(mod, p)) continue;
                    if (!hasHeadroomAt(mod, p, 3)) continue;
                    if (!clearShotFrom(mc, p, warden)) continue;
                    double d = p.distSqr(playerPos);
                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    private static boolean clearShotFrom(Minecraft mc, BlockPos pos, Warden warden) {
        net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(
                pos.getX() + 0.5D, pos.getY() + 1.62D, pos.getZ() + 0.5D);
        net.minecraft.world.phys.Vec3 end = warden.getEyePosition();
        net.minecraft.world.phys.HitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, warden));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return true;
        return hit.getLocation().distanceToSqr(start) >= end.distanceToSqr(start) - 1.0D;
    }

    /**
     * Returns true if the warden cannot step out of its current position.
     * Warden step height = 1.0 block, so it can only climb onto an adjacent block
     * that is at the SAME Y level as its feet AND has air above it (walkable surface).
     * We also check Y+1 neighbours — the warden can step up 1 block.
     * This works for any trench shape: a 2×1 trench has one air neighbour inside the
     * trench, but that neighbour has no solid floor at the same level to step onto.
     */
    private static boolean isWardenInPit(AltoClef mod, Warden warden) {
        if (mod.getWorld() == null || warden == null) return false;
        BlockPos feet = warden.blockPosition();
        if (!mod.getWorld().getBlockState(feet).isAir()) return false;
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos side = feet.relative(d);
            BlockState sideState = mod.getWorld().getBlockState(side);
            // Can the warden step ONTO this adjacent block (step-up escape)?
            // Condition: the adjacent block is solid on top AND the block above it is air (can stand there)
            if (sideState.isFaceSturdy(mod.getWorld(), side, Direction.UP)
                    && mod.getWorld().getBlockState(side.above()).isAir()) {
                return false; // warden can step up here and escape
            }
        }
        return true;
    }

    /**
     * 2×2×2 pit: 2 wide (X), 2 long (Z), 2 deep.
     * 8 blocks total. Warden can't step out regardless of approach angle.
     */
    private static List<BlockPos> buildLureTrenchTargets(BlockPos centre) {
        List<BlockPos> targets = new ArrayList<>();
        for (int y = 0; y > -2; y--) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    targets.add(centre.offset(dx, y, dz));
                }
            }
        }
        return targets; // 8 blocks
    }

    /**
     * Find a 2×2 footprint area ~22 blocks from the warden, in the direction toward the player.
     * At 22 blocks: player digs from 26+ blocks away (safe from sonic boom), baitPos ends up
     * ~14 blocks from warden (within warden vibration range), holdPos is 32+ blocks (safe retreat).
     */
    private static BlockPos findLurePitPos(AltoClef mod, Warden warden) {
        if (mod.getPlayer() == null || mod.getWorld() == null || warden == null) return null;
        BlockPos wPos = warden.blockPosition();
        BlockPos pPos = mod.getPlayer().blockPosition();
        // Normalized direction from warden toward player
        double wdx = pPos.getX() - wPos.getX(), wdz = pPos.getZ() - wPos.getZ();
        double wdLen = Math.sqrt(wdx * wdx + wdz * wdz);
        double tpx = wdLen > 0 ? wdx / wdLen : 0, tpz = wdLen > 0 ? wdz / wdLen : 0;

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -28; dx <= 28; dx += 2) {
            for (int dz = -28; dz <= 28; dz += 2) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos candidate = wPos.offset(dx, dy, dz);
                    if (!isGoodLurePit(mod, candidate)) continue;
                    double cdx = candidate.getX() - wPos.getX();
                    double cdz = candidate.getZ() - wPos.getZ();
                    double distToWarden = Math.sqrt(cdx * cdx + cdz * cdz);
                    // Alignment toward player (dot product of normalized vectors)
                    double alignment = distToWarden > 0 ? (cdx * tpx + cdz * tpz) / distToWarden : 0;
                    // Prefer ~22 blocks from warden and directly in line toward player
                    double distPenalty = Math.pow(distToWarden - 22.0, 2);
                    double alignPenalty = (1.0 - alignment) * 80; // heavy penalty for wrong direction
                    double score = distPenalty + alignPenalty;
                    if (score < bestScore) { bestScore = score; best = candidate; }
                }
            }
        }
        return best;
    }

    /** True if a 2×2×2 pit can safely be dug here. */
    private static boolean isRegionClear(AltoClef mod, BlockPos a, BlockPos b) {
        if (mod.getWorld() == null) return false;
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mod.getWorld().hasChunkAt(pos)) return false;
                    if (!mod.getWorld().getBlockState(pos).isAir()) return false;
                }
        return true;
    }

    private static boolean isGoodLurePit(AltoClef mod, BlockPos centre) {
        if (mod.getWorld() == null) return false;
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                for (int y = 0; y > -2; y--) {
                    BlockPos p = centre.offset(dx, y, dz);
                    if (isProtectedLootBlock(mod, p)) return false;
                    if (!isSafeTrapBlock(mod, p)) return false;
                    if (mod.getWorld().getBlockState(p).isAir() && y == 0) return false;
                }
                // Solid floor below the pit
                BlockPos floor = centre.offset(dx, -2, dz);
                if (!mod.getWorld().getBlockState(floor).isFaceSturdy(mod.getWorld(), floor, Direction.UP)) return false;
                // Headroom above
                if (!mod.getWorld().getBlockState(centre.offset(dx, 1, dz)).isAir()) return false;
            }
        }
        // Stand spot on far side must be solid
        BlockPos standFloor = centre.offset(0, -1, 2);
        if (!mod.getWorld().getBlockState(standFloor).isFaceSturdy(mod.getWorld(), standFloor, Direction.UP)) return false;
        return true;
    }

    /**
     * True during the ~40-tick sonic boom windup. Detected client-side via the
     * public AnimationState field — safe to poll every tick.
     */
    private static boolean isSonicBoomWinding(Warden warden) {
        try {
            return warden.sonicBoomAnimationState.isStarted();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isActiveSonicThreat(AltoClef mod, Warden warden) {
        if (mod.getPlayer() == null || warden == null || !warden.isAlive()) return false;
        if (!isSonicBoomWinding(warden)) return false;
        // Sonic boom is already committed once the animation starts — don't check getTarget()
        // because client-side getTarget() often returns null during sonic boom windup.
        double dist = warden.distanceTo(mod.getPlayer());
        return dist <= SONIC_BOOM_SAFE_DIST + 2;
    }

    /** True during the warden's melee swing animation. */
    private static boolean isWardenMeleeSwinging(Warden warden) {
        try {
            return warden.attackAnimationState.isStarted();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True when warden is mid-melee-swing AND close enough to hit us. */
    private static boolean isWardenMeleeClose(AltoClef mod, Warden warden) {
        if (mod.getPlayer() == null || warden == null || !warden.isAlive()) return false;
        return isWardenMeleeSwinging(warden) && warden.distanceTo(mod.getPlayer()) < MELEE_SAFE_DIST;
    }

    private static final Item[] ALL_WOOL = {
        Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL,
        Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
        Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
        Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL
    };
    private static final Block[] ALL_WOOL_BLOCKS = {
        Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL, Blocks.LIGHT_BLUE_WOOL,
        Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.PINK_WOOL, Blocks.GRAY_WOOL,
        Blocks.LIGHT_GRAY_WOOL, Blocks.CYAN_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL,
        Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.RED_WOOL, Blocks.BLACK_WOOL
    };

    private static int countWool(AltoClef mod) {
        int total = 0;
        for (Item wool : ALL_WOOL) total += mod.getItemStorage().getItemCountInventoryOnly(wool);
        return total;
    }

    private Task harvestNearbyWool(AltoClef mod, BlockPos origin, int targetCount) {
        if (mod.getWorld() == null || mod.getPlayer() == null || countWool(mod) >= targetCount) return null;
        BlockPos center = origin == null ? mod.getPlayer().blockPosition() : origin;
        BlockPos player = mod.getPlayer().blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int radius = origin == null ? 18 : 42;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = center.getY() - 8; y <= center.getY() + 10; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mod.getWorld().hasChunkAt(pos)) continue;
                    if (!isWoolBlock(mod.getWorld().getBlockState(pos).getBlock())) continue;
                    if (isProtectedLootBlock(mod, pos) || !isSafeTrapBlock(mod, pos)) continue;
                    double dist = player.distSqr(pos);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = pos;
                    }
                }
            }
        }
        return best == null ? null : new DestroyBlockTask(best);
    }

    private static boolean isWoolBlock(Block block) {
        for (Block wool : ALL_WOOL_BLOCKS) {
            if (block == wool) return true;
        }
        return false;
    }

    private static boolean equipAnyWool(AltoClef mod) {
        for (Item wool : ALL_WOOL) {
            if (mod.getItemStorage().hasItemInventoryOnly(wool)) {
                return mod.getSlotHandler().forceEquipItem(wool);
            }
        }
        return false;
    }

    private static Block bestWoolBlock(AltoClef mod) {
        for (int i = 0; i < ALL_WOOL.length; i++) {
            if (mod.getItemStorage().hasItemInventoryOnly(ALL_WOOL[i])) return ALL_WOOL_BLOCKS[i];
        }
        return Blocks.WHITE_WOOL;
    }

    // Ranged weapon priority: bow > crossbow > trident
    private static final Item[] RANGED_PRIORITY = {
        Items.BOW, Items.CROSSBOW, Items.TRIDENT
    };
    // Melee weapon priority: netherite > diamond > iron > stone > wood > fist (null = fist)
    private static final Item[] MELEE_PRIORITY = {
        Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD,
        Items.NETHERITE_AXE,   Items.DIAMOND_AXE,   Items.IRON_AXE,   Items.STONE_AXE,   Items.WOODEN_AXE
    };

    private static boolean hasRangedWeapon(AltoClef mod) {
        boolean hasArrows = mod.getItemStorage().getItemCountInventoryOnly(Items.ARROW) > 0;
        boolean hasRocket = mod.getItemStorage().getItemCountInventoryOnly(Items.FIREWORK_ROCKET) > 0;
        if (mod.getItemStorage().hasItemInventoryOnly(Items.BOW) && hasArrows) return true;
        if (mod.getItemStorage().hasItemInventoryOnly(Items.CROSSBOW) && (hasArrows || hasRocket)) return true;
        if (mod.getItemStorage().hasItemInventoryOnly(Items.TRIDENT)) return true;
        return false;
    }

    private static void equipBestRangedWeapon(AltoClef mod) {
        for (Item item : RANGED_PRIORITY) {
            if (mod.getItemStorage().hasItemInventoryOnly(item)) {
                mod.getSlotHandler().forceEquipItem(item);
                return;
            }
        }
    }

    private static void equipBestMeleeWeapon(AltoClef mod) {
        for (Item item : MELEE_PRIORITY) {
            if (mod.getItemStorage().hasItemInventoryOnly(item)) {
                mod.getSlotHandler().forceEquipItem(item);
                return;
            }
        }
        // Fist — nothing to equip
    }

    private static String bestWeaponName(AltoClef mod) {
        for (Item item : RANGED_PRIORITY) {
            if (mod.getItemStorage().hasItemInventoryOnly(item))
                return BuiltInRegistries.ITEM.getKey(item).getPath();
        }
        for (Item item : MELEE_PRIORITY) {
            if (mod.getItemStorage().hasItemInventoryOnly(item))
                return BuiltInRegistries.ITEM.getKey(item).getPath();
        }
        return "fist";
    }

    /** Returns a BlockPos `dist` blocks from `from` in the direction away from `toward`. */
    private static BlockPos awayFrom(BlockPos toward, BlockPos from, int dist) {
        double dx = from.getX() - toward.getX();
        double dz = from.getZ() - toward.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) return from.north(dist);
        return new BlockPos(
            toward.getX() + (int) (dx / len * dist),
            from.getY(),
            toward.getZ() + (int) (dz / len * dist)
        );
    }

    private static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("[Warden] " + msg), false);
    }
}
