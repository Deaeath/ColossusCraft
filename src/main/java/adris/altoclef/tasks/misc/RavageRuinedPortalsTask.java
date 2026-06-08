package adris.altoclef.tasks.misc;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;

public class RavageRuinedPortalsTask extends Task {
    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        mod.runBaritone("explore");
        setDebugState("Search ruined portals");
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof RavageRuinedPortalsTask;
    }

    @Override
    protected String toDebugString() {
        return "Ravage ruined portals";
    }
}
