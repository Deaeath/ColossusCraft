package adris.altoclef.tasks.stupid;

import adris.altoclef.tasks.movement.TimeoutWanderTask;
import net.minecraft.core.BlockPos;

public class TerminatorTask extends TimeoutWanderTask {
    public TerminatorTask(BlockPos origin, int radius) {
        super(true);
    }
}
