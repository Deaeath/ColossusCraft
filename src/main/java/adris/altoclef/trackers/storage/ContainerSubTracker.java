package adris.altoclef.trackers.storage;

import adris.altoclef.Debug;
import adris.altoclef.trackers.Tracker;
import adris.altoclef.trackers.TrackerManager;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.Slot;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class ContainerSubTracker extends Tracker {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<ContainerCache> caches = new ArrayList<>();
    private BlockPos lastBlockPosInteraction;

    // Persistence/lifecycle state.
    private String loadedWorldKey;
    private boolean wasContainerOpen;
    private boolean dirtySinceSave;

    public ContainerSubTracker(TrackerManager manager) {
        super(manager);
    }

    public void remember(ContainerCache cache) {
        caches.removeIf(existing -> existing.getBlockPos().equals(cache.getBlockPos())
                && existing.getDimension().equals(cache.getDimension()));
        caches.add(cache);
    }

    public void setLastBlockPosInteraction(BlockPos pos) {
        lastBlockPosInteraction = pos;
    }

    public boolean hasItem(Predicate<ContainerCache> accept, Item... items) {
        return caches.stream().filter(accept).anyMatch(cache -> cache.hasItem(items));
    }

    public boolean hasItem(Item... items) {
        return hasItem(cache -> true, items);
    }

    public Optional<ContainerCache> getContainerAtPosition(BlockPos pos) {
        return caches.stream().filter(cache -> cache.getBlockPos().equals(pos)).findFirst();
    }

    public Optional<ContainerCache> getEnderChestStorage() {
        return caches.stream().filter(cache -> cache.getType() == ContainerType.ENDER_CHEST).findFirst();
    }

    public List<ContainerCache> getCachedContainers(Predicate<ContainerCache> accept) {
        return caches.stream().filter(accept).toList();
    }

    public List<ContainerCache> getCachedContainers(ContainerType... types) {
        return getCachedContainers(cache -> {
            for (ContainerType type : types) {
                if (cache.getType() == type) return true;
            }
            return false;
        });
    }

    public Optional<ContainerCache> getClosestTo(Vec3 pos, Predicate<ContainerCache> accept) {
        return caches.stream()
                .filter(accept)
                .min((a, b) -> Double.compare(a.getBlockPos().getCenter().distanceToSqr(pos), b.getBlockPos().getCenter().distanceToSqr(pos)));
    }

    public Optional<ContainerCache> getClosestTo(Vec3 pos, ContainerType... types) {
        return getClosestTo(pos, cache -> {
            for (ContainerType type : types) {
                if (cache.getType() == type) return true;
            }
            return false;
        });
    }

    public List<ContainerCache> getContainersWithItem(Item... items) {
        return getCachedContainers(cache -> cache.hasItem(items));
    }

    public Optional<ContainerCache> getClosestWithItem(Vec3 pos, Item... items) {
        return getClosestTo(pos, cache -> cache.hasItem(items));
    }

    public BlockPos getLastBlockPosInteraction() {
        return lastBlockPosInteraction;
    }

    /** Current dimension id ("minecraft:overworld" etc.), or "" if not in game. */
    public String currentDimension() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? "" : player.level().dimension().location().toString();
    }

    /**
     * Runs every client tick (independent of the bot being active). Scans the open container into the
     * cache, and drives per-world load/save of the persisted cache. Safe to call when not in game.
     */
    public void tickScan() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        String worldKey = worldKey(mc);

        // World changed (joined/left a world or server): persist the old, swap in the new.
        if (!Objects.equals(worldKey, loadedWorldKey)) {
            if (loadedWorldKey != null && dirtySinceSave) {
                saveToDisk(loadedWorldKey);
            }
            caches.clear();
            lastBlockPosInteraction = null;
            wasContainerOpen = false;
            dirtySinceSave = false;
            loadedWorldKey = worldKey;
            if (worldKey != null) {
                loadFromDisk(worldKey);
            }
        }

        if (player == null) return;

        boolean open = scanOpenContainer(mc, player);
        // Save when a container we scanned just closed (captures the final contents).
        if (wasContainerOpen && !open && dirtySinceSave && loadedWorldKey != null) {
            saveToDisk(loadedWorldKey);
            dirtySinceSave = false;
        }
        wasContainerOpen = open;
    }

    private boolean scanOpenContainer(Minecraft mc, LocalPlayer player) {
        Screen screen = mc.screen;
        if (!(screen instanceof AbstractContainerScreen)) return false;
        // Exclude the player's own inventory/crafting screen (it isn't a placed block container).
        if (player.containerMenu == player.inventoryMenu) return false;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) return false;
        BlockPos pos = blockHit.getBlockPos();
        ContainerType type = typeForBlock(player.level(), pos);
        if (type == ContainerType.UNKNOWN) return false;

        ContainerCache cache = new ContainerCache(pos, type, player.level().dimension().location().toString());
        for (Slot slot : Slot.getCurrentScreenSlots()) {
            if (Slot.isCursor(slot)) continue;
            if (slot.isSlotInPlayerInventory()) continue;
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            if (!stack.isEmpty()) {
                cache.setItemCount(stack.getItem(), cache.getItemCount(stack.getItem()) + stack.getCount());
            }
        }
        if (cache.getItemCounts().isEmpty() && mod.getMobDefenseChain().isDoingAcrobatics()) {
            return true;
        }
        setLastBlockPosInteraction(pos);
        remember(cache);
        dirtySinceSave = true;
        return true;
    }

    private static ContainerType typeForBlock(Level level, BlockPos pos) {
        Block b = level.getBlockState(pos).getBlock();
        if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST) return ContainerType.CHEST;
        if (b == Blocks.ENDER_CHEST) return ContainerType.ENDER_CHEST;
        if (b == Blocks.BARREL) return ContainerType.BARREL;
        if (b instanceof ShulkerBoxBlock) return ContainerType.SHULKER_BOX;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
        if (id != null && id.getNamespace().equals("lootr")) {
            if (id.getPath().contains("barrel")) return ContainerType.BARREL;
            if (id.getPath().contains("chest")) return ContainerType.CHEST;
            if (id.getPath().contains("shulker")) return ContainerType.SHULKER_BOX;
        }
        if (b == Blocks.FURNACE) return ContainerType.FURNACE;
        if (b == Blocks.BLAST_FURNACE) return ContainerType.BLAST_FURNACE;
        if (b == Blocks.SMOKER) return ContainerType.SMOKER;
        return ContainerType.UNKNOWN;
    }

    // --- persistence ---

    private static String worldKey(Minecraft mc) {
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isEmpty()) {
            return "server_" + sanitize(server.ip);
        }
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return "world_" + sanitize(mc.getSingleplayerServer().getWorldData().getLevelName());
        }
        return null;
    }

    private static String sanitize(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static Path fileFor(String worldKey) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("colossuscraft").resolve("containers").resolve(worldKey + ".json");
    }

    private void saveToDisk(String worldKey) {
        try {
            Persisted out = new Persisted();
            for (ContainerCache cache : caches) {
                Entry e = new Entry();
                e.dim = cache.getDimension();
                e.x = cache.getBlockPos().getX();
                e.y = cache.getBlockPos().getY();
                e.z = cache.getBlockPos().getZ();
                e.type = cache.getType().name();
                for (Map.Entry<Item, Integer> ic : cache.getItemCounts().entrySet()) {
                    e.items.put(BuiltInRegistries.ITEM.getKey(ic.getKey()).toString(), ic.getValue());
                }
                out.containers.add(e);
            }
            Path file = fileFor(worldKey);
            Files.createDirectories(file.getParent());
            MAPPER.writeValue(file.toFile(), out);
        } catch (Exception e) {
            Debug.logWarning("Failed to save container cache: " + e.getMessage());
        }
    }

    private void loadFromDisk(String worldKey) {
        try {
            Path file = fileFor(worldKey);
            if (!Files.exists(file)) return;
            Persisted in = MAPPER.readValue(file.toFile(), Persisted.class);
            if (in == null || in.containers == null) return;
            for (Entry e : in.containers) {
                ContainerType type;
                try {
                    type = ContainerType.valueOf(e.type);
                } catch (IllegalArgumentException ex) {
                    type = ContainerType.UNKNOWN;
                }
                ContainerCache cache = new ContainerCache(new BlockPos(e.x, e.y, e.z), type, e.dim == null ? "" : e.dim);
                if (e.items != null) {
                    for (Map.Entry<String, Integer> ic : e.items.entrySet()) {
                        ResourceLocation id = ResourceLocation.tryParse(ic.getKey());
                        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                            cache.setItemCount(BuiltInRegistries.ITEM.get(id), ic.getValue());
                        }
                    }
                }
                remember(cache);
            }
        } catch (Exception e) {
            Debug.logWarning("Failed to load container cache: " + e.getMessage());
        }
    }

    public static class Persisted {
        public List<Entry> containers = new ArrayList<>();
    }

    public static class Entry {
        public String dim = "";
        public int x;
        public int y;
        public int z;
        public String type = ContainerType.UNKNOWN.name();
        public Map<String, Integer> items = new LinkedHashMap<>();
    }

    @Override
    protected void updateState() {
    }

    @Override
    protected void reset() {
        // Persist before clearing so leaving the world keeps the index.
        if (loadedWorldKey != null && dirtySinceSave) {
            saveToDisk(loadedWorldKey);
            dirtySinceSave = false;
        }
        caches.clear();
        lastBlockPosInteraction = null;
        wasContainerOpen = false;
        loadedWorldKey = null;
    }
}
