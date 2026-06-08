package adris.altoclef.tasks.construction;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.function.Predicate;

public class PlaceBlockNearbyTask extends Task {
    private final Predicate<BlockPos> canPlaceHere;
    private final Block[] toPlace;

    public PlaceBlockNearbyTask(Predicate<BlockPos> canPlaceHere, Block... toPlace) {
        this.canPlaceHere = canPlaceHere;
        this.toPlace = toPlace;
    }

    public PlaceBlockNearbyTask(Block... toPlace) {
        this(pos -> true, toPlace);
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return null;
        BlockPos center = mod.getPlayer().blockPosition();
        for (BlockPos scan : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
            BlockPos pos = scan.immutable();
            if (mod.getWorld().getBlockState(pos).isAir() && mod.getWorld().getBlockState(pos.below()).isSolid() && canPlaceHere.test(pos)) {
                return new PlaceBlockTask(pos, toPlace);
            }
        }
        setDebugState("No nearby place spot");
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof PlaceBlockNearbyTask task && Arrays.equals(task.toPlace, toPlace);
    }

    @Override
    protected String toDebugString() {
        return "Place nearby";
    }
}
