package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.world.phys.Vec3;

public class GoInDirectionXZTask extends Task {
    private final Vec3 origin;
    private final Vec3 delta;
    private final double sidePenalty;

    public GoInDirectionXZTask(Vec3 origin, Vec3 delta, double sidePenalty) {
        this.origin = origin;
        this.delta = delta;
        this.sidePenalty = sidePenalty;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        int x = (int) Math.round(origin.x + delta.x * 64);
        int z = (int) Math.round(origin.z + delta.z * 64);
        return new GetToXZTask(x, z);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GoInDirectionXZTask task && task.origin.equals(origin) && task.delta.equals(delta) && task.sidePenalty == sidePenalty;
    }

    @Override
    protected String toDebugString() {
        return "Go direction XZ";
    }
}
