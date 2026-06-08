package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

public class StoreInContainerTask extends Task {
    private final BlockPos targetContainer;
    private final boolean getIfNotPresent;
    private final ItemTarget[] toStore;
    private int useCooldown;

    public StoreInContainerTask(BlockPos targetContainer, boolean getIfNotPresent, ItemTarget... toStore) {
        this.targetContainer = targetContainer;
        this.getIfNotPresent = getIfNotPresent;
        this.toStore = toStore;
    }

    @Override
    protected void onStart(AltoClef mod) {
        useCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (Minecraft.getInstance().screen instanceof ContainerScreen) {
            return new StoreInOpenContainerTask(toStore);
        }
        if (mod.getPlayer() != null && mod.getPlayer().blockPosition().distSqr(targetContainer) > 16) {
            return new GetToBlockTask(targetContainer);
        }
        if (useCooldown-- <= 0 && mod.getController() != null && mod.getPlayer() != null) {
            useCooldown = 10;
            LookHelper.lookAt(mod, targetContainer);
            mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(targetContainer), Direction.UP, targetContainer, false));
        }
        setDebugState("Open container");
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return new StoreInOpenContainerTask(toStore).isFinished(mod);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof StoreInContainerTask task
                && task.targetContainer.equals(targetContainer)
                && task.getIfNotPresent == getIfNotPresent
                && Arrays.equals(task.toStore, toStore);
    }

    @Override
    protected String toDebugString() {
        return "Store in container " + targetContainer.toShortString();
    }
}
