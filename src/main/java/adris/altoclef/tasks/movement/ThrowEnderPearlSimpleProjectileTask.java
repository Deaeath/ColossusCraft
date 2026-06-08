package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public class ThrowEnderPearlSimpleProjectileTask extends Task {
    private final BlockPos target;
    private boolean thrown;

    public ThrowEnderPearlSimpleProjectileTask(BlockPos target) {
        this.target = target;
    }

    @Override
    protected void onStart(AltoClef mod) {
        thrown = false;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.ENDER_PEARL)) {
            return new CollectItemTask(new ItemTarget(Items.ENDER_PEARL, 1));
        }
        if (mod.getSlotHandler().forceEquipItem(new ItemTarget(Items.ENDER_PEARL, 1), false)
                && mod.getController() != null && mod.getPlayer() != null) {
            LookHelper.lookAt(mod, target);
            mod.getController().useItem(mod.getPlayer(), InteractionHand.MAIN_HAND);
            thrown = true;
        }
        setDebugState("Throw pearl");
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return thrown;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ThrowEnderPearlSimpleProjectileTask task && task.target.equals(target);
    }

    @Override
    protected String toDebugString() {
        return "Throw pearl " + target.toShortString();
    }
}
