package adris.altoclef.tasks.slot;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.slots.Slot;
import net.minecraft.world.inventory.ClickType;

public class ReceiveCraftingOutputSlotTask extends Task {
    private final Slot outputSlot;
    private final int targetCount;
    private boolean clicked;

    public ReceiveCraftingOutputSlotTask(Slot outputSlot, int targetCount) {
        this.outputSlot = outputSlot;
        this.targetCount = targetCount;
    }

    @Override
    protected void onStart(AltoClef mod) {
        clicked = false;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        mod.getSlotHandler().clickSlot(outputSlot, 0, ClickType.QUICK_MOVE);
        clicked = true;
        setDebugState("Receive output x" + targetCount);
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
        return other instanceof ReceiveCraftingOutputSlotTask task && task.outputSlot.equals(outputSlot) && task.targetCount == targetCount;
    }

    @Override
    protected String toDebugString() {
        return "Receive crafting output";
    }
}
