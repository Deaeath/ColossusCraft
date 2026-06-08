package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Items;

public class ShearSheepTask extends Task {
    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.SHEARS)) {
            return new CollectItemTask(new ItemTarget(Items.SHEARS, 1));
        }
        Sheep sheep = mod.getEntityTracker().getTrackedEntities(Sheep.class).stream()
                .filter(s -> s.isAlive() && !s.isSheared())
                .findFirst().orElse(null);
        if (sheep == null) {
            setDebugState("No sheep");
            return null;
        }
        if (mod.getPlayer() != null && mod.getPlayer().distanceToSqr(sheep) > 9) {
            return new GetToEntityTask(sheep, 2);
        }
        if (mod.getSlotHandler().forceEquipItem(Items.SHEARS) && mod.getController() != null && mod.getPlayer() != null) {
            LookHelper.lookAt(mod, sheep.getEyePosition());
            mod.getController().interact(mod.getPlayer(), sheep, InteractionHand.MAIN_HAND);
        }
        setDebugState("Shear sheep");
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ShearSheepTask;
    }

    @Override
    protected String toDebugString() {
        return "Shear sheep";
    }
}
