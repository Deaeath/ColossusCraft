package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Water-bucket clutch. While in a dangerous freefall this raycasts straight down to the block we are
 * about to land on, looks at it, equips a water bucket and places it just before impact so we land in
 * water instead of taking fall damage. Simplified from upstream's cone-clutch (no off-center steering),
 * but performs the real place + exposes the placed position so the fall chain can re-collect the water.
 */
public class MLGBucketTask extends Task {

    private static final double CAST_DOWN_DISTANCE = 40;
    private BlockPos _placedPos;

    @Override
    protected void onStart(AltoClef mod) {
        mod.getClientBaritone().getPathingBehavior().cancelEverything();
        _placedPos = null;
        // Look down; it helps the raycast and the place.
        if (mod.getPlayer() != null) {
            mod.getPlayer().setXRot(90);
        }
    }

    @Override
    protected Task onTick(AltoClef mod) {
        // Always faster.
        mod.getInputControls().hold(Input.SPRINT);

        Optional<BlockPos> landOn = getBlockWeWillLandOn(mod);
        if (landOn.isEmpty()) {
            setDebugState("Wait for it...");
            mod.getInputControls().release(Input.JUMP);
            return null;
        }

        BlockPos toPlaceOn = landOn.get();
        if (!WorldHelper.isSolid(mod, toPlaceOn)) {
            toPlaceOn = toPlaceOn.below();
        }
        BlockPos willLandIn = toPlaceOn.above();
        // Already water? We're good.
        if (mod.getWorld().getBlockState(willLandIn).getBlock() == Blocks.WATER) {
            setDebugState("Waiting to fall into water");
            mod.getInputControls().release(Input.CLICK_RIGHT);
            return null;
        }

        if (mod.getWorld().dimensionType().ultraWarm() || !mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
            setDebugState("No clutch item");
            return null;
        }

        Optional<Rotation> reach = LookHelper.getReach(toPlaceOn);
        if (reach.isPresent()) {
            setDebugState("Performing MLG");
            LookHelper.lookAt(mod, reach.get());
            if (mod.getSlotHandler().forceEquipItem(Items.WATER_BUCKET)) {
                if (mod.getClientBaritone().getPlayerContext().isLookingAt(toPlaceOn)) {
                    _placedPos = willLandIn;
                    mod.getInputControls().tryPress(Input.CLICK_RIGHT);
                }
            }
        } else {
            setDebugState("Waiting to reach target block...");
        }
        return null;
    }

    private Optional<BlockPos> getBlockWeWillLandOn(AltoClef mod) {
        if (mod.getPlayer() == null) return Optional.empty();
        Vec3 origin = mod.getPlayer().position();
        Vec3 end = origin.add(0, -CAST_DOWN_DISTANCE, 0);
        BlockHitResult hit = mod.getWorld().clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, mod.getPlayer()));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        return Optional.ofNullable(hit.getBlockPos());
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.getClientBaritone().getPathingBehavior().cancelEverything();
        mod.getInputControls().release(Input.CLICK_RIGHT);
        mod.getInputControls().release(Input.SPRINT);
        mod.getInputControls().release(Input.JUMP);
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return mod.getPlayer() != null && (mod.getPlayer().isSwimming() || mod.getPlayer().isInWater()
                || mod.getPlayer().onGround() || mod.getPlayer().onClimbable());
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof MLGBucketTask;
    }

    @Override
    protected String toDebugString() {
        return "Epic gaemer moment";
    }

    public BlockPos getWaterPlacedPos() {
        return _placedPos;
    }
}
