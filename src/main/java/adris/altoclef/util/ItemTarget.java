package adris.altoclef.util;

import adris.altoclef.TaskCatalogue;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ItemTarget {
    private static final int BASICALLY_INFINITY = 99999999;

    public static ItemTarget EMPTY = new ItemTarget(new Item[0], 0);
    private Item[] itemMatches;
    private int targetCount;
    private String catalogueName;
    private boolean infinite;

    public ItemTarget(Item[] items, int targetCount) {
        itemMatches = items;
        this.targetCount = targetCount;
    }

    public ItemTarget(String catalogueName, int targetCount) {
        this.catalogueName = catalogueName;
        itemMatches = TaskCatalogue.getItemMatches(catalogueName);
        this.targetCount = targetCount;
    }

    public ItemTarget(String catalogueName) {
        this(catalogueName, 1);
    }

    public ItemTarget(Item item, int targetCount) {
        this(new Item[]{item}, targetCount);
    }

    public ItemTarget(Item[] items) {
        this(items, 1);
    }

    public ItemTarget(Item item) {
        this(item, 1);
    }

    public ItemTarget(ItemTarget toCopy, int newCount) {
        if (toCopy.itemMatches != null) {
            itemMatches = new Item[toCopy.itemMatches.length];
            System.arraycopy(toCopy.itemMatches, 0, itemMatches, 0, toCopy.itemMatches.length);
        }
        catalogueName = toCopy.catalogueName;
        targetCount = newCount;
        infinite = toCopy.infinite;
    }

    public static boolean nullOrEmpty(ItemTarget target) {
        return target == null || target == EMPTY;
    }

    public static Item[] getMatches(ItemTarget... targets) {
        Set<Item> result = new HashSet<>();
        for (ItemTarget target : targets) {
            result.addAll(Arrays.asList(target.getMatches()));
        }
        return result.toArray(Item[]::new);
    }

    public ItemTarget infinite() {
        infinite = true;
        return this;
    }

    public Item[] getMatches() {
        return itemMatches != null ? itemMatches : new Item[0];
    }

    public int getTargetCount() {
        return infinite ? BASICALLY_INFINITY : targetCount;
    }

    public boolean matches(Item item) {
        if (itemMatches != null) {
            for (Item match : itemMatches) {
                if (match != null && match.equals(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isCatalogueItem() {
        return catalogueName != null;
    }

    public String getCatalogueName() {
        return catalogueName;
    }

    public boolean isEmpty() {
        return itemMatches == null || itemMatches.length == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ItemTarget other)) {
            return false;
        }
        if (infinite != other.infinite) {
            return false;
        }
        if (!infinite && targetCount != other.targetCount) {
            return false;
        }
        return Arrays.equals(itemMatches, other.itemMatches);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (isEmpty()) {
            result.append("(empty)");
        } else if (isCatalogueItem()) {
            result.append(catalogueName);
        } else {
            result.append("[");
            for (int i = 0; i < itemMatches.length; i++) {
                Item item = itemMatches[i];
                result.append(item == null ? "(null)" : ItemHelper.trimItemName(item.getDescriptionId()));
                if (i + 1 < itemMatches.length) {
                    result.append(",");
                }
            }
            result.append("]");
        }
        if (infinite) {
            result.append(" x infinity");
        } else if (!isEmpty()) {
            result.append(" x ").append(targetCount);
        }
        return result.toString();
    }
}
