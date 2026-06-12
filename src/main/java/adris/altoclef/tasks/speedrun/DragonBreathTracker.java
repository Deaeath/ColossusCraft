package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.AreaEffectCloud;

import java.util.HashSet;

public class DragonBreathTracker {
    private final HashSet<BlockPos> breathBlocks = new HashSet<>();

    public void reset() {
        breathBlocks.clear();
    }

    public void updateBreath(AltoClef mod) {
        breathBlocks.clear();
        for (AreaEffectCloud cloud : mod.getEntityTracker().getTrackedEntities(AreaEffectCloud.class)) {
            for (BlockPos bad : WorldHelper.getBlocksTouchingBox(mod, cloud.getBoundingBox())) {
                breathBlocks.add(bad.immutable());
            }
        }
    }

    public boolean isTouchingDragonBreath(BlockPos pos) {
        return breathBlocks.contains(pos);
    }

    public Task getRunAwayTask() {
        return new TimeoutWanderTask();
    }
}
