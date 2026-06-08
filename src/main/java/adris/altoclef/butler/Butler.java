package adris.altoclef.butler;

import adris.altoclef.AltoClef;

public class Butler {
    private final AltoClef mod;

    public Butler(AltoClef mod) {
        this.mod = mod;
    }

    public void tick() {
    }

    public boolean isUserAuthorized(String username) {
        return true;
    }
}
