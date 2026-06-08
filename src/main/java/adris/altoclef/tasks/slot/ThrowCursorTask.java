package adris.altoclef.tasks.slot;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.CursorSlot;
import net.minecraft.world.inventory.ClickType;

public class ThrowCursorTask extends Task {
    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (!StorageHelper.getItemStackInCursorSlot().isEmpty()) {
            mod.getSlotHandler().clickSlot(CursorSlot.SLOT, 0, ClickType.THROW);
        }
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.getItemStackInCursorSlot().isEmpty();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ThrowCursorTask;
    }

    @Override
    protected String toDebugString() {
        return "Throw cursor";
    }
}
