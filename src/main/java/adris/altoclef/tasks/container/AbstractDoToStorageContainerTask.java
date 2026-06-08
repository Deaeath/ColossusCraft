package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

import java.util.Optional;

public abstract class AbstractDoToStorageContainerTask extends Task {
    protected abstract Optional<BlockPos> getContainerTarget();

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return getContainerTarget().map(pos -> (Task) new StoreInContainerTask(pos, false)).orElse(null);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }
}
