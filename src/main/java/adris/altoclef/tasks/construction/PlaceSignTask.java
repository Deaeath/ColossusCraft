package adris.altoclef.tasks.construction;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class PlaceSignTask extends Task {
    private final BlockPos target;
    private final String message;

    public PlaceSignTask(BlockPos pos, String message) {
        this.target = pos;
        this.message = message;
    }

    public PlaceSignTask(String message) {
        this(null, message);
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.OAK_SIGN, Items.SPRUCE_SIGN, Items.BIRCH_SIGN, Items.JUNGLE_SIGN,
                Items.ACACIA_SIGN, Items.DARK_OAK_SIGN, Items.MANGROVE_SIGN, Items.CHERRY_SIGN, Items.BAMBOO_SIGN)) {
            return new CollectItemTask(new ItemTarget(Items.OAK_SIGN, 1));
        }
        setDebugState("Place sign text=" + message);
        return target == null ? new PlaceBlockNearbyTask(Blocks.OAK_SIGN) : new PlaceBlockTask(target, Blocks.OAK_SIGN);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof PlaceSignTask task && java.util.Objects.equals(task.target, target) && task.message.equals(message);
    }

    @Override
    protected String toDebugString() {
        return "Place sign";
    }
}
