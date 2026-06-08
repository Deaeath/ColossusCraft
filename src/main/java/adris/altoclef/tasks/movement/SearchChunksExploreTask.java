package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.world.level.ChunkPos;

public abstract class SearchChunksExploreTask extends Task {
    private int commandCooldown;

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (commandCooldown-- <= 0) {
            commandCooldown = 100;
            mod.runBaritone("explore");
        }
        setDebugState("Explore chunks");
        return null;
    }

    protected boolean isChunkWithinSearchSpace(AltoClef mod, ChunkPos pos) {
        return true;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }
}
