package adris.altoclef.tasks.slot;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.slots.Slot;
import net.minecraft.world.inventory.ClickType;

public class ClickSlotTask extends Task {
    private final Slot slot;
    private boolean clicked;

    public ClickSlotTask(Slot slot) {
        this.slot = slot;
    }

    @Override
    protected void onStart(AltoClef mod) {
        clicked = false;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        mod.getSlotHandler().clickSlot(slot, 0, ClickType.PICKUP);
        clicked = true;
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return clicked;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ClickSlotTask task && task.slot.equals(slot);
    }

    @Override
    protected String toDebugString() {
        return "Click slot " + slot;
    }
}
