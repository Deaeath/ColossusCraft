package adris.altoclef.tasks.misc;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.world.InteractionHand;

import java.util.Arrays;

public class EquipArmorTask extends Task {
    private final ItemTarget[] targets;
    private int useCooldown;

    public EquipArmorTask(ItemTarget... targets) {
        this.targets = targets;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.isArmorEquippedAll(mod, targets);
    }

    @Override
    protected void onStart(AltoClef mod) {
        useCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        for (ItemTarget target : targets) {
            if (!StorageHelper.isArmorEquipped(mod, target)) {
                if (!mod.getItemStorage().hasItemInventoryOnly(target.getMatches())) {
                    setDebugState("Collecting " + target);
                    return new CollectItemTask(target);
                }
                setDebugState("Equipping " + target);
                if (mod.getSlotHandler().forceEquipItem(target, false)
                        && mod.getController() != null
                        && mod.getPlayer() != null
                        && useCooldown-- <= 0) {
                    useCooldown = 8;
                    mod.getController().useItem(mod.getPlayer(), InteractionHand.MAIN_HAND);
                }
                return null;
            }
        }
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EquipArmorTask task && Arrays.equals(task.targets, targets);
    }

    @Override
    protected String toDebugString() {
        return "Equip armor " + Arrays.toString(targets);
    }
}
