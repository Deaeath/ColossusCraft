package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.world.level.ChunkPos;

public class GetToChunkTask extends Task {
    private final ChunkPos pos;

    public GetToChunkTask(ChunkPos pos) {
        this.pos = pos;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return new GetToXZTask(pos.getMiddleBlockX(), pos.getMiddleBlockZ());
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GetToChunkTask task && task.pos.equals(pos);
    }

    @Override
    protected String toDebugString() {
        return "Get to chunk " + pos;
    }
}
