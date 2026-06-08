package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;

import java.util.Arrays;
import java.util.Optional;

public class PickupDroppedItemTask extends Task {
    private static boolean gettingPickaxeFirst;
    private final ItemTarget[] itemTargets;
    private final boolean freeInventoryIfFull;
    private int commandCooldown;

    public PickupDroppedItemTask(ItemTarget[] itemTargets, boolean freeInventoryIfFull) {
        this.itemTargets = itemTargets;
        this.freeInventoryIfFull = freeInventoryIfFull;
    }

    public PickupDroppedItemTask(ItemTarget target, boolean freeInventoryIfFull) {
        this(new ItemTarget[]{target}, freeInventoryIfFull);
    }

    public PickupDroppedItemTask(Item item, int targetCount, boolean freeInventoryIfFull) {
        this(new ItemTarget(item, targetCount), freeInventoryIfFull);
    }

    public PickupDroppedItemTask(Item item, int targetCount) {
        this(item, targetCount, true);
    }

    public static boolean isIsGettingPickaxeFirst(AltoClef mod) {
        return gettingPickaxeFirst;
    }

    public boolean isCollectingPickaxeForThis() {
        return false;
    }

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Optional<ItemEntity> closest = mod.getEntityTracker().getClosestItemDrop(itemTargets);
        if (closest.isEmpty()) {
            setDebugState("No matching drop");
            return null;
        }
        ItemEntity entity = closest.get();
        if (commandCooldown-- <= 0) {
            commandCooldown = 20;
            mod.runBaritone("goto " + entity.blockPosition().getX() + " " + entity.blockPosition().getY() + " " + entity.blockPosition().getZ());
        }
        setDebugState("Pickup " + Arrays.toString(itemTargets));
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.itemTargetsMetInventory(mod, itemTargets);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof PickupDroppedItemTask task
                && Arrays.equals(task.itemTargets, itemTargets)
                && task.freeInventoryIfFull == freeInventoryIfFull;
    }

    @Override
    protected String toDebugString() {
        return "Pickup dropped item";
    }
}
