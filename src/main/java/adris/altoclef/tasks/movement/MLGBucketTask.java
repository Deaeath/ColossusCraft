package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Items;

public class MLGBucketTask extends Task {
    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.WATER_BUCKET)) {
            return new CollectItemTask(new ItemTarget(Items.WATER_BUCKET, 1));
        }
        mod.getSlotHandler().forceEquipItem(new ItemTarget(Items.WATER_BUCKET, 1), false);
        setDebugState("Ready water bucket");
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof MLGBucketTask;
    }

    @Override
    protected String toDebugString() {
        return "MLG bucket";
    }
}
