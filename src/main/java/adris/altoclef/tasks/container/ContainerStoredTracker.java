package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Item;

import java.util.function.Predicate;

public class ContainerStoredTracker {
    public ContainerStoredTracker(Predicate<Object> acceptSlot) {
    }

    public void startTracking() {
    }

    public void stopTracking() {
    }

    public int getStoredCount(Item... items) {
        return 0;
    }

    public ItemTarget[] getUnstoredItemTargetsYouCanStore(AltoClef mod, ItemTarget... targets) {
        return targets;
    }
}
