package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.function.Supplier;

public abstract class RunAwayFromEntitiesTask extends Task {
    private final Supplier<List<Entity>> runAwaySupplier;
    private final double distanceToRun;
    private final boolean xz;
    private final double penalty;

    public RunAwayFromEntitiesTask(Supplier<List<Entity>> toRunAwayFrom, double distanceToRun, boolean xz, double penalty) {
        this.runAwaySupplier = toRunAwayFrom;
        this.distanceToRun = distanceToRun;
        this.xz = xz;
        this.penalty = penalty;
    }

    public RunAwayFromEntitiesTask(Supplier<List<Entity>> toRunAwayFrom, double distanceToRun, double penalty) {
        this(toRunAwayFrom, distanceToRun, false, penalty);
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        List<Entity> entities = runAwaySupplier.get();
        if (entities == null || entities.isEmpty()) return null;
        BlockPos[] positions = entities.stream().map(Entity::blockPosition).toArray(BlockPos[]::new);
        setDebugState("Run from entities");
        return new RunAwayFromPositionTask(distanceToRun, positions);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other != null && other.getClass() == getClass();
    }

    @Override
    protected String toDebugString() {
        return "Run from entities xz=" + xz + " penalty=" + penalty;
    }
}
