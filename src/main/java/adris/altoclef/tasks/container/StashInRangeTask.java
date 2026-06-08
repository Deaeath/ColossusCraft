package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;

import java.util.Arrays;

public class StashInRangeTask extends Task {
    private final BlockPos a;
    private final BlockPos b;
    private final ItemTarget[] targets;

    public StashInRangeTask(BlockPos a, BlockPos b, ItemTarget... targets) {
        this.a = a;
        this.b = b;
        this.targets = targets == null ? new ItemTarget[0] : targets;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (mod.getPlayer() == null) return null;
        if (!inside(mod.getPlayer().blockPosition())) {
            setDebugState("Move to stash region");
            return new GetToBlockTask(center());
        }
        if (Minecraft.getInstance().screen instanceof ContainerScreen) {
            setDebugState("Deposit in open stash container");
            return new StoreInOpenContainerTask(targets);
        }
        setDebugState("At stash region; open a chest/container");
        return null;
    }

    private boolean inside(BlockPos pos) {
        return pos.getX() >= Math.min(a.getX(), b.getX()) && pos.getX() <= Math.max(a.getX(), b.getX())
                && pos.getY() >= Math.min(a.getY(), b.getY()) && pos.getY() <= Math.max(a.getY(), b.getY())
                && pos.getZ() >= Math.min(a.getZ(), b.getZ()) && pos.getZ() <= Math.max(a.getZ(), b.getZ());
    }

    private BlockPos center() {
        return new BlockPos((a.getX() + b.getX()) / 2, (a.getY() + b.getY()) / 2, (a.getZ() + b.getZ()) / 2);
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return mod.getPlayer() != null && inside(mod.getPlayer().blockPosition())
                && new StoreInOpenContainerTask(targets).isFinished(mod);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof StashInRangeTask task
                && task.a.equals(a)
                && task.b.equals(b)
                && Arrays.equals(task.targets, targets);
    }

    @Override
    protected String toDebugString() {
        return "Stash " + (targets.length == 0 ? "all" : Arrays.toString(targets));
    }
}
