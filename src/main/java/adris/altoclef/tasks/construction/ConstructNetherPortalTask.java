package adris.altoclef.tasks.construction;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class ConstructNetherPortalTask extends Task {
    private BlockPos base;
    private int commandCooldown;
    private int useCooldown;

    @Override
    protected void onStart(AltoClef mod) {
        base = null;
        commandCooldown = 0;
        useCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null || mod.getController() == null) return null;
        if (mod.getBlockTracker().getNearestWithinRange(mod.getPlayer().blockPosition(), 16, Blocks.NETHER_PORTAL).isPresent()) {
            setDebugState("Portal built");
            return null;
        }
        if (mod.getItemStorage().getItemCountInventoryOnly(Items.OBSIDIAN) < 14) {
            setDebugState("Collect obsidian");
            if (!mod.getItemStorage().hasItemInventoryOnly(Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
                return new CollectItemTask(new ItemTarget(Items.DIAMOND_PICKAXE, 1));
            }
            return new CollectItemTask(new ItemTarget(Items.OBSIDIAN, 14));
        }
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.FLINT_AND_STEEL)) {
            setDebugState("Collect flint and steel");
            return new CollectItemTask(new ItemTarget(Items.FLINT_AND_STEEL, 1));
        }
        if (base == null) {
            base = findBase(mod);
            if (base == null) {
                setDebugState("No flat portal build spot");
                return null;
            }
        }
        for (BlockPos pos : frame(base)) {
            if (!mod.getWorld().getBlockState(pos).is(Blocks.OBSIDIAN)) {
                return placeBlock(mod, pos, Items.OBSIDIAN, "Place obsidian");
            }
        }
        if (mod.getSlotHandler().forceEquipItem(Items.FLINT_AND_STEEL) && useCooldown-- <= 0) {
            useCooldown = 10;
            BlockPos bottom = base.offset(1, 0, 0);
            LookHelper.lookAt(mod, bottom);
            mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(bottom), Direction.UP, bottom, false));
        }
        setDebugState("Light portal");
        return null;
    }

    private Task placeBlock(AltoClef mod, BlockPos pos, net.minecraft.world.item.Item item, String state) {
        if (mod.getPlayer().blockPosition().distSqr(pos) > 16.0) {
            if (commandCooldown-- <= 0) {
                commandCooldown = 30;
                mod.runBaritone("goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            }
            setDebugState("Move to portal frame");
            return null;
        }
        Placement placement = findPlacementNeighbor(mod, pos);
        if (placement == null) {
            setDebugState("No support for " + pos.toShortString());
            return null;
        }
        if (mod.getSlotHandler().forceEquipItem(item) && useCooldown-- <= 0) {
            useCooldown = 8;
            LookHelper.lookAt(mod, placement.neighbor);
            mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(placement.neighbor), placement.face, placement.neighbor, false));
        }
        setDebugState(state + " " + pos.toShortString());
        return null;
    }

    private Placement findPlacementNeighbor(AltoClef mod, BlockPos target) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = target.relative(direction);
            BlockState state = mod.getWorld().getBlockState(neighbor);
            if (!state.isAir() && state.isSolid()) {
                return new Placement(neighbor, direction.getOpposite());
            }
        }
        return null;
    }

    private BlockPos findBase(AltoClef mod) {
        BlockPos player = mod.getPlayer().blockPosition();
        for (BlockPos candidate : BlockPos.betweenClosed(player.offset(2, 0, -2), player.offset(5, 1, 2))) {
            boolean ok = true;
            for (BlockPos pos : frame(candidate)) {
                BlockState state = mod.getWorld().getBlockState(pos);
                if (!state.isAir() && !state.is(Blocks.OBSIDIAN) && !state.canBeReplaced()) {
                    ok = false;
                    break;
                }
            }
            if (ok && mod.getWorld().getBlockState(candidate.below()).isSolid()) {
                return candidate.immutable();
            }
        }
        return null;
    }

    private List<BlockPos> frame(BlockPos base) {
        List<BlockPos> result = new ArrayList<>();
        for (int x = 0; x <= 3; x++) {
            result.add(base.offset(x, 0, 0));
            result.add(base.offset(x, 4, 0));
        }
        for (int y = 1; y <= 3; y++) {
            result.add(base.offset(0, y, 0));
            result.add(base.offset(3, y, 0));
        }
        return result;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return mod.getPlayer() != null
                && mod.getBlockTracker().getNearestWithinRange(mod.getPlayer().blockPosition(), 16, Blocks.NETHER_PORTAL).isPresent();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ConstructNetherPortalTask;
    }

    @Override
    protected String toDebugString() {
        return "Construct Nether portal";
    }

    private record Placement(BlockPos neighbor, Direction face) {
    }
}
