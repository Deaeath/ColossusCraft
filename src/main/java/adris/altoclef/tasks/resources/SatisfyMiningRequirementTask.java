package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.StorageHelper;

public class SatisfyMiningRequirementTask extends Task {
    private final MiningRequirement requirement;

    public SatisfyMiningRequirementTask(MiningRequirement requirement) {
        this.requirement = requirement;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        return StorageHelper.miningRequirementMet(mod, requirement) ? null : new CollectItemTask(new ItemTarget(requirement.getMinimumPickaxe(), 1));
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.miningRequirementMet(mod, requirement);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SatisfyMiningRequirementTask task && task.requirement == requirement;
    }

    @Override
    protected String toDebugString() {
        return "Satisfy mining " + requirement;
    }
}
