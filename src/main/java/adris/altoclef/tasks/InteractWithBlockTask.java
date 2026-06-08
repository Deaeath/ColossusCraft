package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class InteractWithBlockTask extends Task {
    private final ItemTarget item;
    private final Direction direction;
    private final BlockPos target;
    private final boolean stopOnInteract;
    private int useCooldown;
    private boolean interacted;

    public InteractWithBlockTask(BlockPos target) {
        this(null, Direction.UP, target, true);
    }

    public InteractWithBlockTask(ItemTarget item, Direction direction, BlockPos target, boolean stopOnInteract) {
        this.item = item;
        this.direction = direction;
        this.target = target;
        this.stopOnInteract = stopOnInteract;
    }

    @Override
    protected void onStart(AltoClef mod) {
        useCooldown = 0;
        interacted = false;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (mod.getPlayer() != null && mod.getPlayer().blockPosition().distSqr(target) > 16) {
            return new GetToBlockTask(target);
        }
        if (item != null && !mod.getSlotHandler().forceEquipItem(item, false)) {
            return new CollectItemTask(item);
        }
        if (useCooldown-- <= 0 && mod.getController() != null && mod.getPlayer() != null) {
            useCooldown = 8;
            LookHelper.lookAt(mod, target);
            mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(target), direction, target, false));
            interacted = true;
        }
        setDebugState("Interact block");
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return stopOnInteract && interacted;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof InteractWithBlockTask task && task.target.equals(target);
    }

    @Override
    protected String toDebugString() {
        return "Interact " + target.toShortString();
    }
}
