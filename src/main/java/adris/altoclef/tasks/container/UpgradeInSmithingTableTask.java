package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;

public class UpgradeInSmithingTableTask extends ResourceTask {
    private final ItemTarget target;

    public UpgradeInSmithingTableTask(ItemTarget target) {
        super(target);
        this.target = target;
    }

    public UpgradeInSmithingTableTask(ItemTarget tool, ItemTarget materials, ItemTarget result) {
        this(result);
    }

    @Override
    protected void onResourceStart(AltoClef mod) {
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        setDebugState("Smithing upgrade " + target);
        return null;
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof UpgradeInSmithingTableTask task && task.target.equals(target);
    }

    @Override
    protected String toDebugString() {
        return "Upgrade in smithing table";
    }
}
