package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.core.BlockPos;

import java.util.Arrays;

public class StoreInStashTask extends Task {
    private final BlockPos start;
    private final BlockPos end;
    private final ItemTarget[] toStore;

    public StoreInStashTask(BlockPos start, BlockPos end, ItemTarget... toStore) {
        this.start = start;
        this.end = end;
        this.toStore = toStore;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return new StashInRangeTask(start, end, toStore);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof StoreInStashTask task
                && task.start.equals(start)
                && task.end.equals(end)
                && Arrays.equals(task.toStore, toStore);
    }

    @Override
    protected String toDebugString() {
        return "Store in stash";
    }
}
