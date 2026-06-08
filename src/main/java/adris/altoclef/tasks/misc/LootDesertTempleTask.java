package adris.altoclef.tasks.misc;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class LootDesertTempleTask extends Task {
    private final BlockPos temple;

    public LootDesertTempleTask(BlockPos temple) {
        this.temple = temple;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        setDebugState("Loot desert temple " + temple);
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof LootDesertTempleTask task && task.temple.equals(temple);
    }

    @Override
    protected String toDebugString() {
        return "Loot desert temple";
    }
}
