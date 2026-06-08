package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.world.item.Items;

public class CollectFoodTask extends Task {
    private final ItemTarget foodTarget;
    private final CollectItemTask collectTask;

    public CollectFoodTask(int foodCount) {
        foodTarget = new ItemTarget(new net.minecraft.world.item.Item[]{
                Items.BREAD,
                Items.CARROT,
                Items.POTATO,
                Items.BAKED_POTATO,
                Items.APPLE,
                Items.MELON_SLICE,
                Items.COOKED_BEEF,
                Items.BEEF,
                Items.COOKED_PORKCHOP,
                Items.PORKCHOP,
                Items.COOKED_CHICKEN,
                Items.CHICKEN,
                Items.COOKED_MUTTON,
                Items.MUTTON,
                Items.COOKED_COD,
                Items.COD,
                Items.COOKED_SALMON,
                Items.SALMON
        }, foodCount);
        collectTask = new CollectItemTask(foodTarget);
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.itemTargetsMetInventory(mod, foodTarget);
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return collectTask;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        collectTask.stop(mod, interruptTask);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CollectFoodTask task && task.foodTarget.equals(foodTarget);
    }

    @Override
    protected String toDebugString() {
        return "Collect food x " + foodTarget.getTargetCount();
    }
}
