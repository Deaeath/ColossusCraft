package adris.altoclef.tasks.slot;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.Slot;

public class MoveItemToSlotTask extends Task {
    protected final Slot destination;
    protected final ItemTarget target;

    public MoveItemToSlotTask(Slot destination, ItemTarget target) {
        this.destination = destination;
        this.target = target;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (target.matches(StorageHelper.getItemStackInSlot(destination).getItem())) return null;
        Slot source = mod.getItemStorage().getSlotsWithItemScreen(target.getMatches()).stream()
                .filter(slot -> !slot.equals(destination))
                .findFirst().orElse(null);
        if (source == null) return null;
        return new ClickSlotTask(source);
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return target.matches(StorageHelper.getItemStackInSlot(destination).getItem());
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof MoveItemToSlotTask task && task.destination.equals(destination) && task.target.equals(target);
    }

    @Override
    protected String toDebugString() {
        return "Move item to slot";
    }
}
