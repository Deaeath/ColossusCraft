package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class OpenCraftingTableTask extends Task {
    private int useCooldown;

    @Override
    protected void onStart(AltoClef mod) {
        useCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (StorageHelper.isBigCraftingOpen()) return null;
        if (mod.getPlayer() == null || mod.getWorld() == null || mod.getController() == null) return null;
        BlockPos table = findNearbyTable(mod);
        if (table != null) {
            setDebugState("Open crafting table");
            useBlock(mod, table, Direction.UP);
            return null;
        }
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.CRAFTING_TABLE)) {
            setDebugState("Collect crafting table");
            return new CollectItemTask(new ItemTarget(Items.CRAFTING_TABLE, 1));
        }
        BlockPos placeAt = findPlaceSpot(mod);
        if (placeAt == null) {
            setDebugState("No nearby crafting table placement");
            return null;
        }
        if (mod.getSlotHandler().forceEquipItem(Items.CRAFTING_TABLE) && useCooldown-- <= 0) {
            useCooldown = 8;
            setDebugState("Place crafting table");
            BlockPos support = placeAt.below();
            LookHelper.lookAt(mod, support);
            mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
        }
        return null;
    }

    private BlockPos findNearbyTable(AltoClef mod) {
        BlockPos center = mod.getPlayer().blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            if (mod.getWorld().getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private BlockPos findPlaceSpot(AltoClef mod) {
        BlockPos center = mod.getPlayer().blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
            if (mod.getWorld().getBlockState(pos).isAir() && mod.getWorld().getBlockState(pos.below()).isSolid()) {
                return pos.immutable();
            }
        }
        return null;
    }

    private void useBlock(AltoClef mod, BlockPos pos, Direction direction) {
        if (useCooldown-- > 0) return;
        useCooldown = 8;
        LookHelper.lookAt(mod, pos);
        mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), direction, pos, false));
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.isBigCraftingOpen();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof OpenCraftingTableTask;
    }

    @Override
    protected String toDebugString() {
        return "Open crafting table";
    }
}
