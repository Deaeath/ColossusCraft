package com.local.altoclef;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Comparator;
import java.util.List;

/**
 * Spawns a squad of iron golems near a warden, lures it into them, hides, and finishes the survivor.
 *
 * Usage: /cc warden golems [count]   (default 6 golems)
 *
 * Phases:
 *  GATHER   → collect 4*N iron blocks + N carved pumpkins
 *  POSITION → walk to a spot 15–20 blocks from the nearest warden
 *  BUILD    → place N golem T-structures (pumpkin last per structure)
 *  LURE     → walk toward warden until anger > 0, then back off
 *  HIDE     → retreat 30 blocks out and 4 blocks up
 *  WAIT     → idle until warden is dead or ≤ 50 HP
 *  FINISH   → KillEntityTask on the weakened warden
 */
public class WardenGolemTask extends Task {

    // ── Golem T-structure offsets relative to a base BlockPos ──────────────
    // Index 0-3: iron blocks (placed first); index 4: pumpkin (triggers spawn)
    private static final int[][] GOLEM_OFFSETS = {
        {0, 0, 0},   // leg
        {-1, 1, 0},  // arm L
        {0, 1, 0},   // torso
        {1, 1, 0},   // arm R
        {0, 2, 0},   // head (carved pumpkin — placed LAST)
    };

    private final int golemCount;

    // ── State ───────────────────────────────────────────────────────────────
    private Phase phase = Phase.GATHER;
    private int golemsBuilt = 0;        // how many full structures placed
    private int blockStep = 0;          // which block within the current structure
    private BlockPos buildOrigin = null; // anchor for the current golem structure
    private int lureTimer = 0;
    private int waitTimer = 0;
    private boolean luredOnce = false;

    private enum Phase { GATHER, POSITION, BUILD, LURE, HIDE, WAIT, FINISH }

    public WardenGolemTask(int golemCount) {
        this.golemCount = Math.max(1, golemCount);
    }

    @Override
    protected void onStart(AltoClef mod) {
        phase = Phase.GATHER;
        golemsBuilt = 0;
        blockStep = 0;
        buildOrigin = null;
        lureTimer = 0;
        luredOnce = false;
        say("Warden golem squad: " + golemCount + " golems");
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Warden warden = nearestWarden(mod);

        switch (phase) {

            // ── 1. GATHER ───────────────────────────────────────────────────
            case GATHER -> {
                int ironNeeded = golemCount * 4;
                int ironHave = mod.getItemStorage().getItemCountInventoryOnly(Items.IRON_BLOCK);
                if (ironHave < ironNeeded) {
                    setDebugState("Collecting iron blocks " + ironHave + "/" + ironNeeded);
                    return new CollectItemTask(new ItemTarget(Items.IRON_BLOCK, ironNeeded));
                }
                int pumpkinHave = mod.getItemStorage().getItemCountInventoryOnly(Items.CARVED_PUMPKIN);
                if (pumpkinHave < golemCount) {
                    setDebugState("Collecting carved pumpkins " + pumpkinHave + "/" + golemCount);
                    return new CollectItemTask(new ItemTarget(
                        new net.minecraft.world.item.Item[]{Items.CARVED_PUMPKIN, Items.JACK_O_LANTERN}, golemCount));
                }
                phase = Phase.POSITION;
                say("Materials ready. Moving to build spot.");
            }

            // ── 2. POSITION ─────────────────────────────────────────────────
            case POSITION -> {
                if (warden == null) {
                    setDebugState("No warden found — waiting");
                    return null;
                }
                // Aim for a spot 18 blocks from the warden, on our side
                BlockPos target = offsetFrom(warden.blockPosition(), mod.getPlayer().blockPosition(), 18);
                if (mod.getPlayer().blockPosition().distSqr(target) < 9) {
                    buildOrigin = target;
                    phase = Phase.BUILD;
                    golemsBuilt = 0;
                    blockStep = 0;
                    say("Build spot reached. Constructing golems.");
                } else {
                    setDebugState("Moving to build spot");
                    return new GetToBlockTask(target);
                }
            }

            // ── 3. BUILD ────────────────────────────────────────────────────
            case BUILD -> {
                if (golemsBuilt >= golemCount) {
                    phase = Phase.LURE;
                    lureTimer = 0;
                    say("All golems built. Luring warden.");
                    return null;
                }
                // Origin for this golem: space them 5 blocks apart in X
                BlockPos origin = buildOrigin.offset(golemsBuilt * 5, 0, 0);
                int[] off = GOLEM_OFFSETS[blockStep];
                BlockPos target = origin.offset(off[0], off[1], off[2]);

                // Check if block already placed
                if (!(mod.getWorld().getBlockState(target).isAir())) {
                    advanceBuildStep();
                    return null;
                }

                boolean isPumpkin = blockStep == 4;
                Block block = isPumpkin ? Blocks.CARVED_PUMPKIN : Blocks.IRON_BLOCK;
                setDebugState("Placing " + block.getName().getString() + " for golem " + (golemsBuilt + 1));
                return new PlaceBlockTask(target, block);
            }

            // ── 4. LURE ─────────────────────────────────────────────────────
            case LURE -> {
                if (warden == null) { phase = Phase.WAIT; return null; }
                lureTimer++;
                if (!luredOnce && lureTimer < 80) {
                    // Walk toward warden to trigger anger
                    setDebugState("Approaching warden to lure");
                    return new GetToBlockTask(warden.blockPosition().offset(3, 0, 0));
                }
                luredOnce = true;
                phase = Phase.HIDE;
                say("Lured. Retreating.");
            }

            // ── 5. HIDE ─────────────────────────────────────────────────────
            case HIDE -> {
                if (warden == null) { phase = Phase.WAIT; return null; }
                BlockPos hideSpot = offsetFrom(warden.blockPosition(), mod.getPlayer().blockPosition(), 35)
                        .above(4);
                if (mod.getPlayer().blockPosition().distSqr(hideSpot) < 25) {
                    phase = Phase.WAIT;
                    waitTimer = 0;
                    say("Hiding. Let the golems work.");
                } else {
                    setDebugState("Retreating to safe position");
                    return new GetToBlockTask(hideSpot);
                }
            }

            // ── 6. WAIT ─────────────────────────────────────────────────────
            case WAIT -> {
                waitTimer++;
                if (warden == null) {
                    say("Warden dead.");
                    return null; // task complete
                }
                if (warden.getHealth() <= 50 || waitTimer > 1200) {
                    phase = Phase.FINISH;
                    say("Warden weakened (" + (int) warden.getHealth() + " HP). Moving in.");
                }
                setDebugState("Waiting — warden HP " + (int) warden.getHealth());
            }

            // ── 7. FINISH ───────────────────────────────────────────────────
            case FINISH -> {
                if (warden == null) return null; // dead, done
                setDebugState("Finishing warden");
                return new KillEntityTask(warden);
            }
        }
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof WardenGolemTask;
    }

    @Override
    protected String toDebugString() {
        return "Warden golem strategy (" + phase + ")";
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return phase == Phase.WAIT && nearestWarden(mod) == null;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void advanceBuildStep() {
        blockStep++;
        if (blockStep >= GOLEM_OFFSETS.length) {
            blockStep = 0;
            golemsBuilt++;
        }
    }

    /** Find the closest living warden to the player. */
    private Warden nearestWarden(AltoClef mod) {
        if (mod.getWorld() == null || mod.getPlayer() == null) return null;
        return mod.getWorld().getEntitiesOfClass(Warden.class,
                mod.getPlayer().getBoundingBox().inflate(60),
                w -> w.isAlive())
            .stream()
            .min(Comparator.comparingDouble(w -> w.distanceTo(mod.getPlayer())))
            .orElse(null);
    }

    /**
     * Returns a BlockPos that is `dist` blocks away from `from` in the direction
     * of `toward`, snapped to integer coords.
     */
    private BlockPos offsetFrom(BlockPos from, BlockPos toward, int dist) {
        double dx = toward.getX() - from.getX();
        double dz = toward.getZ() - from.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) return toward;
        return new BlockPos(
            from.getX() + (int) (dx / len * dist),
            from.getY(),
            from.getZ() + (int) (dz / len * dist)
        );
    }

    private static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("[Warden] " + msg), false);
    }
}
