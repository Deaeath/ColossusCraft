package adris.altoclef.tasks.construction.compound;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Items;

public class ConstructIronGolemTask extends Task {
    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.IRON_BLOCK) || mod.getItemStorage().getItemCountInventoryOnly(Items.IRON_BLOCK) < 4) {
            return new CollectItemTask(new ItemTarget(Items.IRON_BLOCK, 4));
        }
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.CARVED_PUMPKIN, Items.PUMPKIN)) {
            return new CollectItemTask(new ItemTarget(new net.minecraft.world.item.Item[]{Items.CARVED_PUMPKIN, Items.PUMPKIN}, 1));
        }
        setDebugState("Manual golem placement pending");
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ConstructIronGolemTask;
    }

    @Override
    protected String toDebugString() {
        return "Construct iron golem";
    }
}
