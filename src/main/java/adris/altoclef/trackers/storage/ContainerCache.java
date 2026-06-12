package adris.altoclef.trackers.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ContainerCache {
    private final BlockPos blockPos;
    private final ContainerType type;
    private final String dimension;
    private final Map<Item, Integer> itemCounts = new HashMap<>();

    public ContainerCache(BlockPos blockPos, ContainerType type) {
        this(blockPos, type, "");
    }

    public ContainerCache(BlockPos blockPos, ContainerType type, String dimension) {
        this.blockPos = blockPos;
        this.type = type;
        this.dimension = dimension == null ? "" : dimension;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public ContainerType getType() {
        return type;
    }

    public String getDimension() {
        return dimension;
    }

    public Map<Item, Integer> getItemCounts() {
        return Collections.unmodifiableMap(itemCounts);
    }

    public int getItemCount(Item item) {
        return itemCounts.getOrDefault(item, 0);
    }

    public void setItemCount(Item item, int count) {
        if (count <= 0) {
            itemCounts.remove(item);
        } else {
            itemCounts.put(item, count);
        }
    }

    public boolean hasItem(Item... items) {
        for (Item item : items) {
            if (getItemCount(item) > 0) {
                return true;
            }
        }
        return false;
    }
}
