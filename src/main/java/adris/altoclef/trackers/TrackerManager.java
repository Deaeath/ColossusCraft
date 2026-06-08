package adris.altoclef.trackers;

import adris.altoclef.AltoClef;

import java.util.ArrayList;
import java.util.List;

public class TrackerManager {

    private final List<Tracker> trackers = new ArrayList<>();
    private final AltoClef mod;
    private boolean wasInGame;

    public TrackerManager(AltoClef mod) {
        this.mod = mod;
    }

    public void tick() {
        boolean inGame = AltoClef.inGame();
        if (!inGame && wasInGame) {
            for (Tracker tracker : trackers) {
                tracker.reset();
            }
        }
        wasInGame = inGame;
        for (Tracker tracker : trackers) {
            tracker.setDirty();
        }
    }

    public void addTracker(Tracker tracker) {
        tracker.mod = mod;
        trackers.add(tracker);
    }
}
