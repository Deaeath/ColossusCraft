package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

public class LootContainerTask extends Task {
    public final BlockPos chest;
    public final List<Item> targets;
    private final Predicate<ItemStack> check;

    public LootContainerTask(BlockPos chestPos, List<Item> items) {
        this(chestPos, items, stack -> true);
    }

    public LootContainerTask(BlockPos chestPos, List<Item> items, Predicate<ItemStack> pred) {
        this.chest = chestPos;
        this.targets = items;
        this.check = pred;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        ItemTarget[] itemTargets = targets.stream().map(item -> new ItemTarget(item, 1)).toArray(ItemTarget[]::new);
        setDebugState("Loot matching " + check);
        return new PickupFromContainerTask(chest, itemTargets);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof LootContainerTask task && task.chest.equals(chest) && task.targets.equals(targets);
    }

    @Override
    protected String toDebugString() {
        return "Loot container";
    }
}
