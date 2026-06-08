package adris.altoclef.tasks.misc;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Items;

public class SleepThroughNightTask extends Task {
    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.WHITE_BED)) {
            return new CollectItemTask(new ItemTarget(Items.WHITE_BED, 1));
        }
        setDebugState("Sleep through night: place/use bed manually pending");
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SleepThroughNightTask;
    }

    @Override
    protected String toDebugString() {
        return "Sleep through night";
    }
}
