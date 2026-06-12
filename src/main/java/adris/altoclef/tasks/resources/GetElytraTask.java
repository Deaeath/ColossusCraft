package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.GetToOuterEndIslandsTask;
import adris.altoclef.tasks.movement.GetToXZTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.resources.GetBuildingMaterialsTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.LocateResultTracker;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Acquire an elytra: travel to the outer End islands, find the End-Ship item frame holding the elytra,
 * knock it out of the frame, and pick it up. Falls back to wandering the End to discover a city/ship.
 */
public class GetElytraTask extends Task {

    private final TimerGame locateTimer = new TimerGame(12);
    private static final int OUTER_END_RADIUS = 800;
    private static final int OUTWARD_SEARCH_STEP = 3000;
    private static final Set<String> persistentTriedFramePositions = new HashSet<>();
    private static boolean persistentFramesLoaded;
    private BlockPos outwardSearchTarget;
    private final Set<UUID> triedFrameIds = new HashSet<>();
    private final Set<BlockPos> triedFramePositions = new HashSet<>();

    @Override
    protected void onStart(AltoClef mod) {
        locateTimer.forceElapse();
        LocateResultTracker.clear();
        loadPersistentTriedFrames();
        outwardSearchTarget = null;
        triedFrameIds.clear();
        triedFramePositions.clear();
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (mod.getPlayer() == null) return null;

        // Already have it.
        if (mod.getItemStorage().hasItem(Items.ELYTRA)) {
            setDebugState("Have elytra");
            return null;
        }

        // A dropped elytra (knocked out of the frame) — grab it.
        if (mod.getEntityTracker().itemDropped(Items.ELYTRA)) {
            setDebugState("Picking up elytra");
            return new PickupDroppedItemTask(Items.ELYTRA, 1);
        }

        // Find the item frame that holds the elytra.
        ItemFrame frame = findElytraFrame(mod);
        if (frame != null) {
            double reach = mod.getModSettings().getEntityReachRange();
            if (frame.distanceToSqr(mod.getPlayer()) <= reach * reach) {
                setDebugState("Knocking elytra out of frame");
                mod.getControllerExtras().attack(frame);
                rememberTriedFrame(frame);
            } else {
                setDebugState("Moving to elytra frame");
                return new GetToBlockTask(frame.blockPosition());
            }
            return null;
        }

        // No frame in sight. We must be in The End and out on the outer islands to find a city.
        if (WorldHelper.getCurrentDimension() != Dimension.END) {
            setDebugState("Go to The End first (need a city/ship for the elytra)");
            return new GetToOuterEndIslandsTask();
        }

        // Ask the server where the nearest End City is, then path/bridge to it.
        if (WorldHelper.inRangeXZ(mod.getPlayer(), new net.minecraft.world.phys.Vec3(0, 64, 0), OUTER_END_RADIUS)) {
            setDebugState("Getting to the outer End islands");
            return new GetToOuterEndIslandsTask();
        }

        requestEndCityLocate();
        Optional<BlockPos> city = LocateResultTracker.lastStructurePos();
        if (city.isPresent()) {
            BlockPos c = city.get();
            double dx = c.getX() - mod.getPlayer().getX();
            double dz = c.getZ() - mod.getPlayer().getZ();
            if (dx * dx + dz * dz > 24 * 24) {
                // Don't bridge into the void empty-handed: top up blocks (mines End stone) when low.
                if (StorageHelper.getBuildingMaterialCount(mod) < 64) {
                    setDebugState("Low on bridging blocks — gathering more");
                    return new GetBuildingMaterialsTask(192);
                }
                setDebugState("Heading to End City " + c.getX() + "," + c.getZ());
                return new GetToXZTask(c.getX(), c.getZ());
            }
            // We're at the city footprint; the ship/frame is up in the air — wander to spot it.
            setDebugState("At End City, scanning for the ship");
            return new TimeoutWanderTask();
        }

        // Still near the central island — get out to the outer ring first.
        return searchOutward(mod);
    }

    private void requestEndCityLocate() {
        if (!locateTimer.elapsed()) return;
        locateTimer.reset();
        if (Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().sendCommand("locate structure minecraft:end_city");
        }
    }

    private Task searchOutward(AltoClef mod) {
        if (StorageHelper.getBuildingMaterialCount(mod) < 64) {
            setDebugState("Low on bridging blocks - gathering more");
            return new GetBuildingMaterialsTask(192);
        }
        BlockPos target = getOutwardSearchTarget(mod);
        setDebugState("Searching outer End toward " + target.getX() + "," + target.getZ());
        return new GetToXZTask(target.getX(), target.getZ());
    }

    private BlockPos getOutwardSearchTarget(AltoClef mod) {
        BlockPos player = mod.getPlayer().blockPosition();
        if (outwardSearchTarget != null && distanceSqXZ(player, outwardSearchTarget) > 64 * 64) {
            return outwardSearchTarget;
        }
        double x = mod.getPlayer().getX();
        double z = mod.getPlayer().getZ();
        double length = Math.sqrt(x * x + z * z);
        if (length < 1) {
            x = 1;
            z = 0;
            length = 1;
        }
        double targetDistance = Math.max(length + OUTWARD_SEARCH_STEP, OUTER_END_RADIUS + OUTWARD_SEARCH_STEP);
        int targetX = (int) Math.round(x / length * targetDistance);
        int targetZ = (int) Math.round(z / length * targetDistance);
        outwardSearchTarget = new BlockPos(targetX, player.getY(), targetZ);
        return outwardSearchTarget;
    }

    private double distanceSqXZ(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private ItemFrame findElytraFrame(AltoClef mod) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        ItemFrame best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ItemFrame itemFrame && itemFrame.getItem().is(Items.ELYTRA)) {
                if (alreadyTriedFrame(itemFrame)) continue;
                double dist = itemFrame.distanceToSqr(mod.getPlayer());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = itemFrame;
                }
            }
        }
        return best;
    }

    private void rememberTriedFrame(ItemFrame frame) {
        triedFrameIds.add(frame.getUUID());
        triedFramePositions.add(frame.blockPosition());
        persistentTriedFramePositions.add(frameKey(frame.blockPosition()));
        savePersistentTriedFrames();
    }

    private boolean alreadyTriedFrame(ItemFrame frame) {
        return triedFrameIds.contains(frame.getUUID()) ||
                triedFramePositions.contains(frame.blockPosition()) ||
                persistentTriedFramePositions.contains(frameKey(frame.blockPosition()));
    }

    private static String frameKey(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        String dimension = mc.level == null ? "unknown" : mc.level.dimension().location().toString();
        return dimension + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static Path persistentFrameFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("colossuscraft-lootr-elytra-frames.txt");
    }

    private static void loadPersistentTriedFrames() {
        if (persistentFramesLoaded) return;
        persistentFramesLoaded = true;
        Path file = persistentFrameFile();
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    persistentTriedFramePositions.add(trimmed);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void savePersistentTriedFrames() {
        Path file = persistentFrameFile();
        try {
            Files.createDirectories(file.getParent());
            ArrayList<String> lines = new ArrayList<>(persistentTriedFramePositions);
            Collections.sort(lines);
            Files.write(file, lines);
        } catch (IOException ignored) {
        }
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.itemTargetsMetInventory(mod, new adris.altoclef.util.ItemTarget(Items.ELYTRA, 1));
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GetElytraTask;
    }

    @Override
    protected String toDebugString() {
        return "Getting elytra";
    }
}
