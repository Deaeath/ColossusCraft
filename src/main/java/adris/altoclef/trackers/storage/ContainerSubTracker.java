package adris.altoclef.trackers.storage;

import adris.altoclef.trackers.Tracker;
import adris.altoclef.trackers.TrackerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class ContainerSubTracker extends Tracker {

    private final List<ContainerCache> caches = new ArrayList<>();
    private BlockPos lastBlockPosInteraction;

    public ContainerSubTracker(TrackerManager manager) {
        super(manager);
    }

    public void remember(ContainerCache cache) {
        caches.removeIf(existing -> existing.getBlockPos().equals(cache.getBlockPos()));
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

    @Override
    protected void updateState() {
    }

    @Override
    protected void reset() {
        caches.clear();
        lastBlockPosInteraction = null;
    }
}
