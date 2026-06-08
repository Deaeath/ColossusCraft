package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;

import java.util.ArrayList;

public class RunAwayFromCreepersTask extends Task {
    private final double distanceToRun;

    public RunAwayFromCreepersTask(double distance) {
        this.distanceToRun = distance;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return new RunAwayFromEntitiesTask(() -> new ArrayList<Entity>(mod.getEntityTracker().getTrackedEntities(Creeper.class)), distanceToRun, 10) {
        };
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof RunAwayFromCreepersTask task && Math.abs(task.distanceToRun - distanceToRun) < 1;
    }

    @Override
    protected String toDebugString() {
        return "Run from creepers";
    }
}
