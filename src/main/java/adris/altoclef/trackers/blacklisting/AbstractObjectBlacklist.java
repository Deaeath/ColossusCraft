package adris.altoclef.trackers.blacklisting;

import adris.altoclef.AltoClef;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractObjectBlacklist<T> {

    private final Map<T, Entry> entries = new HashMap<>();

    public void blackListItem(AltoClef mod, T item, int numberOfFailuresAllowed) {
        Entry entry = entries.computeIfAbsent(item, ignored -> new Entry(numberOfFailuresAllowed));
        entry.allowed = numberOfFailuresAllowed;
        entry.failures++;
        if (mod.getPlayer() != null) {
            double distance = getPos(item).distanceToSqr(mod.getPlayer().position());
            if (distance + 1 < entry.bestDistanceSq) {
                entry.bestDistanceSq = distance;
                entry.failures = 1;
            }
        }
    }

    protected abstract Vec3 getPos(T item);

    public boolean unreachable(T item) {
        Entry entry = entries.get(item);
        return entry != null && entry.failures > entry.allowed;
    }

    public void clear() {
        entries.clear();
    }

    private static class Entry {
        int allowed;
        int failures;
        double bestDistanceSq = Double.POSITIVE_INFINITY;

        Entry(int allowed) {
            this.allowed = allowed;
        }
    }
}
