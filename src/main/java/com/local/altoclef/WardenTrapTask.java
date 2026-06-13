package com.local.altoclef;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import baritone.api.BaritoneAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

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

    // ── Config ───────────────────────────────────────────────────────────────
    private static final int    RETREAT_DIST      = 21;   // just outside sonic boom range
    private static final int    GOLEM_COUNT       = 4;    // golems if mats available
    private static final int    BOW_ARROWS_NEEDED = 32;
    private static final float  FINISH_HP         = 80;   // switch to melee finish below this
    private static final int    DIG_RADIUS        = 1;    // mine 2×2 around warden feet
    private static final int    DIG_DEPTH         = 2;    // pit depth

    // Golem T-structure offsets: indices 0-3 = iron blocks, index 4 = pumpkin (placed last)
    private static final int[][] GOLEM_OFFSETS = {
        {0, 0, 0}, {-1, 1, 0}, {0, 1, 0}, {1, 1, 0}, {0, 2, 0}
    };

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

    public WardenTrapTask(boolean gatherFirst) {
        this.gatherFirst = gatherFirst;
    }

    private enum Phase {
        GATHER, FIND_SHRIEKER, BUILD_GOLEMS, TRIGGER,
        SPRINT_AND_DIG, RETREAT, FIGHT
    }

    // ── Task lifecycle ───────────────────────────────────────────────────────

    @Override
    protected void onStart(AltoClef mod) {
        phase = gatherFirst ? Phase.GATHER : Phase.FIND_SHRIEKER;
        shriekerPos = null;
        warden = null;
        wardenSpawn = null;
        golemsBuilt = 0;
        golemStep = 0;
        triggerTimer = 0;
        digTargets.clear();
        digIndex = 0;
        say("Warden trap: starting (" + (gatherFirst ? "gather first" : "make do") + ")");
    }

    @Override
    protected Task onTick(AltoClef mod) {
        // Always track warden if one spawns
        if (warden == null || !warden.isAlive()) {
            warden = nearestWarden(mod);
        }

        switch (phase) {

            // ── GATHER ───────────────────────────────────────────────────────
            case GATHER -> {
                // Bow
                int arrows = mod.getItemStorage().getItemCountInventoryOnly(Items.ARROW);
                if (!mod.getItemStorage().hasItemInventoryOnly(Items.BOW) &&
                    !mod.getItemStorage().hasItemInventoryOnly(Items.CROSSBOW)) {
                    setDebugState("Collecting bow");
                    return new CollectItemTask(new ItemTarget(Items.BOW, 1));
                }
                if (arrows < BOW_ARROWS_NEEDED) {
                    setDebugState("Collecting arrows " + arrows + "/" + BOW_ARROWS_NEEDED);
                    return new CollectItemTask(new ItemTarget(Items.ARROW, BOW_ARROWS_NEEDED));
                }
                // Iron + pumpkins (best-effort — we won't block if can't get them)
                // Just advance; golems phase will use whatever we have
                phase = Phase.FIND_SHRIEKER;
                say("Resources ready. Finding shrieker.");
            }

            // ── FIND_SHRIEKER ────────────────────────────────────────────────
            case FIND_SHRIEKER -> {
                BlockPos found = findShrieker(mod);
                if (found != null) {
                    shriekerPos = found;
                    int ironHave   = mod.getItemStorage().getItemCountInventoryOnly(Items.IRON_BLOCK);
                    int pumpkinHave= mod.getItemStorage().getItemCountInventoryOnly(Items.CARVED_PUMPKIN)
                                  + mod.getItemStorage().getItemCountInventoryOnly(Items.JACK_O_LANTERN);
                    int possibleGolems = Math.min(ironHave / 4, pumpkinHave);
                    if (possibleGolems > 0) {
                        phase = Phase.BUILD_GOLEMS;
                        say("Shrieker at " + shriekerPos.toShortString() +
                            " — building " + possibleGolems + " golem(s) first");
                    } else {
                        phase = Phase.TRIGGER;
                        say("Shrieker at " + shriekerPos.toShortString() + " — triggering");
                    }
                    return null;
                }
                // Navigate to shrieker search range (wander within 64 blocks looking)
                setDebugState("Searching for sculk shrieker");
                // Stay put and scan; if none found nearby, advance anyway after timeout
                return null;
            }

            // ── BUILD_GOLEMS ─────────────────────────────────────────────────
            case BUILD_GOLEMS -> {
                int ironHave    = mod.getItemStorage().getItemCountInventoryOnly(Items.IRON_BLOCK);
                int pumpkinHave = mod.getItemStorage().getItemCountInventoryOnly(Items.CARVED_PUMPKIN)
                                + mod.getItemStorage().getItemCountInventoryOnly(Items.JACK_O_LANTERN);
                int maxGolems   = Math.min(ironHave / 4, pumpkinHave);

                if (golemsBuilt >= maxGolems || golemsBuilt >= GOLEM_COUNT) {
                    phase = Phase.TRIGGER;
                    say("Golems placed. Triggering shrieker.");
                    return null;
                }

                // Place 5 blocks north of shrieker per golem, spaced 5 apart
                BlockPos origin = shriekerPos.north(6 + golemsBuilt * 5);
                int[] off = GOLEM_OFFSETS[golemStep];
                BlockPos target = origin.offset(off[0], off[1], off[2]);

                if (!mod.getWorld().getBlockState(target).isAir()) {
                    advanceGolemStep(mod);
                    return null;
                }
                boolean isPumpkin = golemStep == 4;
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
                    // Re-enable sneak during dig sprint (we'll suppress sprint ourselves)
                    BaritoneAPI.getSettings().allowSprint.value = true;
                    say("Warden emerging at " + wardenSpawn.toShortString() + ". Digging trap.");
                }

                // If warden has finished emerging (animation no longer active), move on
                if (!warden.emergeAnimationState.isStarted() && triggerTimer > 60) {
                    phase = Phase.RETREAT;
                    say("Dig window closed. Retreating.");
                    AncientCityHelper.setManualSneak(true);
                    return null;
                }

                // Mine next block in the pit: top layer with hoe, bottom with pickaxe
                if (digIndex < digTargets.size()) {
                    BlockPos target = digTargets.get(digIndex);
                    if (mod.getWorld().getBlockState(target).isAir()) {
                        digIndex++;
                        return null;
                    }
                    setDebugState("Digging trap block " + (digIndex + 1) + "/" + digTargets.size());
                    // Equip appropriate tool: hoe for soft surface blocks, pickaxe for rock
                    boolean isTopLayer = (target.getY() == wardenSpawn.getY());
                    if (isTopLayer) {
                        // Hoe shreds dirt/grass/soul soil fastest
                        mod.getSlotHandler().forceEquipItem(
                            new net.minecraft.world.item.Item[]{
                                Items.NETHERITE_HOE, Items.DIAMOND_HOE, Items.IRON_HOE,
                                Items.STONE_HOE, Items.WOODEN_HOE,
                                Items.NETHERITE_SHOVEL, Items.DIAMOND_SHOVEL, Items.IRON_SHOVEL
                            });
                    } else {
                        mod.getSlotHandler().forceEquipItem(
                            new net.minecraft.world.item.Item[]{
                                Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE,
                                Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE
                            });
                    }
                    // Mine this specific block by navigating adjacent and breaking it
                    Block blockToMine = mod.getWorld().getBlockState(target).getBlock();
                    try {
                        BaritoneAPI.getProvider().getPrimaryBaritone()
                            .getMineProcess().mine(1, blockToMine);
                    } catch (Throwable ignored) {}
                    return null;
                }

                // All blocks mined
                phase = Phase.RETREAT;
                say("Trap dug. Retreating.");
                AncientCityHelper.setManualSneak(true);
            }

            // ── RETREAT ──────────────────────────────────────────────────────
            case RETREAT -> {
                if (warden == null) { say("Warden gone."); return null; }
                AncientCityHelper.setManualSneak(false); // move freely to retreat

                double dist = warden.distanceTo(mod.getPlayer());
                if (dist >= RETREAT_DIST) {
                    phase = Phase.FIGHT;
                    BowAimbot.setEnabled(true);
                    say("In position. Bow aimbot on. Dist=" + (int) dist + "b");
                    return null;
                }
                BlockPos safePos = awayFrom(warden.blockPosition(),
                        mod.getPlayer().blockPosition(), RETREAT_DIST + 4);
                setDebugState("Retreating " + (int) dist + " / " + RETREAT_DIST + "b");
                return new GetToBlockTask(safePos);
            }

            // ── FIGHT ────────────────────────────────────────────────────────
            case FIGHT -> {
                if (warden == null || !warden.isAlive()) {
                    BowAimbot.setEnabled(false);
                    say("Warden dead.");
                    return null; // done
                }
                // Hold range — don't let Baritone drift us closer
                double dist = warden.distanceTo(mod.getPlayer());
                if (dist < RETREAT_DIST - 2) {
                    BlockPos safePos = awayFrom(warden.blockPosition(),
                            mod.getPlayer().blockPosition(), RETREAT_DIST + 2);
                    return new GetToBlockTask(safePos);
                }
                // Melee finish when low
                if (warden.getHealth() <= FINISH_HP) {
                    BowAimbot.setEnabled(false);
                    setDebugState("Melee finish — warden HP " + (int) warden.getHealth());
                    return new adris.altoclef.tasks.entity.KillEntityTask(warden);
                }
                setDebugState("Bow fight — warden HP " + (int) warden.getHealth()
                        + " dist=" + (int) dist + "b");
            }
        }
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        BowAimbot.setEnabled(false);
        AncientCityHelper.setManualSneak(false);
        try { BaritoneAPI.getSettings().allowSprint.value = true; } catch (Throwable ignored) {}
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return phase == Phase.FIGHT && (warden == null || !warden.isAlive());
    }

    @Override
    protected boolean isEqual(Task other) { return other instanceof WardenTrapTask; }

    @Override
    protected String toDebugString() { return "WardenTrap(" + phase + ")"; }

    // ── Helpers ──────────────────────────────────────────────────────────────

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
                    if (mod.getWorld().getBlockState(p).getBlock() == Blocks.SCULK_SHRIEKER) {
                        double d = p.distSqr(centre);
                        if (d < bestDist) { best = p; bestDist = d; }
                    }
                }
            }
        }
        return best;
    }

    private Warden nearestWarden(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return null;
        return mod.getWorld()
                .getEntitiesOfClass(Warden.class,
                        mod.getPlayer().getBoundingBox().inflate(60),
                        w -> w.isAlive())
                .stream()
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
