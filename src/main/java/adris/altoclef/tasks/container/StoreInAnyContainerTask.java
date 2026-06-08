package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;

import java.util.Arrays;

public class StoreInAnyContainerTask extends Task {
    private final boolean getIfNotPresent;
    private final ItemTarget[] toStore;

    public StoreInAnyContainerTask(boolean getIfNotPresent, ItemTarget... toStore) {
        this.getIfNotPresent = getIfNotPresent;
        this.toStore = toStore;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (getIfNotPresent) {
            for (ItemTarget target : toStore) {
                if (!StorageHelper.itemTargetsMetInventory(mod, target)) {
                    return new CollectItemTask(target);
                }
            }
        }
        if (Minecraft.getInstance().screen instanceof ContainerScreen) {
            return new StoreInOpenContainerTask(toStore);
        }
        setDebugState("Open any chest/container");
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return new StoreInOpenContainerTask(toStore).isFinished(mod);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof StoreInAnyContainerTask task
                && task.getIfNotPresent == getIfNotPresent
                && Arrays.equals(task.toStore, toStore);
    }

    @Override
    protected String toDebugString() {
        return "Store in any container";
    }
}
