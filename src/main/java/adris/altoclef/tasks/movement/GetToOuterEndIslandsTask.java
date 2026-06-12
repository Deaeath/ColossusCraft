package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.resources.GetBuildingMaterialsTask;
import adris.altoclef.tasks.speedrun.MarvionBeatMinecraftTask;
import adris.altoclef.tasks.squashed.CataloguedResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.baritone.GoalAnd;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalYLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;

import java.util.List;

public class GetToOuterEndIslandsTask extends Task {
    public final int END_ISLAND_START_RADIUS = 800;
    public final Vec3i[] THROW_OFFSETS = {
            new Vec3i(1, -1, 1),
            new Vec3i(1, -1, -1),
            new Vec3i(-1, -1, 1),
            new Vec3i(-1, -1, -1),
            new Vec3i(2, -1, 0),
            new Vec3i(0, -1, 2),
            new Vec3i(-2, -1, 0),
            new Vec3i(0, -1, -2)
    };
    private Task _beatTheGame;
    private ThrowEnderPearlSimpleProjectileTask _throwPearlTask;
    private BlockPos _throwGateway;
    private int _pearlWaitTicks;

    public GetToOuterEndIslandsTask() {

    }

    @Override
    protected void onStart(AltoClef mod) {
        mod.getBehaviour().push();
        mod.getBlockTracker().trackBlock(Blocks.END_GATEWAY);
        _beatTheGame = new MarvionBeatMinecraftTask();
        _throwPearlTask = null;
        _throwGateway = null;
        _pearlWaitTicks = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        java.util.Optional<BlockPos> gatewayOpt = mod.getBlockTracker().getNearestTracking(Blocks.END_GATEWAY);
        if (gatewayOpt.isPresent()) {
            if (!mod.getItemStorage().hasItemInventoryOnly(Items.ENDER_PEARL)) {
                setDebugState("Getting an ender pearl");
                return new CataloguedResourceTask(new ItemTarget(Items.ENDER_PEARL, 1));
            }
            BlockPos gateway = gatewayOpt.get();
            if (_throwGateway == null || !_throwGateway.equals(gateway)) {
                _throwGateway = gateway;
                _throwPearlTask = null;
                _pearlWaitTicks = 0;
            }
            if (_throwPearlTask != null) {
                if (!_throwPearlTask.isFinished(mod)) {
                    setDebugState("Throwing ender pearl into gateway");
                    return _throwPearlTask;
                }
                _throwPearlTask = null;
                _pearlWaitTicks = 80;
            }
            if (_pearlWaitTicks > 0) {
                _pearlWaitTicks--;
                setDebugState("Waiting for gateway teleport");
                return null;
            }
            BlockPos throwPad = nearestThrowPad(mod, gateway);
            int blocksNeeded = throwPad == null ? 64 : Math.abs(mod.getPlayer().getBlockY() - throwPad.getY()) +
                    Math.abs(mod.getPlayer().getBlockX() - throwPad.getX()) +
                    Math.abs(mod.getPlayer().getBlockZ() - throwPad.getZ()) + 24;
            if (StorageHelper.getBuildingMaterialCount(mod) < blocksNeeded) {
                setDebugState("Getting building materials");
                return new GetBuildingMaterialsTask(blocksNeeded);
            }
            Goal goal = makeGoal(gateway);
            BlockPos playerPos = mod.getPlayer().blockPosition();
            if (!goal.isInGoal(playerPos.getX(), playerPos.getY(), playerPos.getZ()) || !mod.getPlayer().onGround()) {
                mod.getClientBaritone().getCustomGoalProcess().setGoal(goal);
                if (!mod.getClientBaritone().getPathingBehavior().isPathing()) {
                    mod.getClientBaritone().getCustomGoalProcess().path();
                }
                setDebugState("Getting to gateway pearl pad");
                return null;
            }
            _throwPearlTask = new ThrowEnderPearlSimpleProjectileTask(gateway);
            setDebugState("Throwing ender pearl into gateway");
            return _throwPearlTask;
        }
        setDebugState("Beating the Game to get to an end gateway");
        return _beatTheGame;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(Blocks.END_GATEWAY);
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GetToOuterEndIslandsTask;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return WorldHelper.getCurrentDimension() == Dimension.END &&
                !WorldHelper.inRangeXZ(new Vec3(0, 64, 0), mod.getPlayer().position(), END_ISLAND_START_RADIUS);
    }

    @Override
    protected String toDebugString() {
        return "Going to outer end islands";
    }

    private Goal makeGoal(BlockPos gateway) {
        Goal[] goals = throwPads(gateway).stream()
                .map(GoalGetToBlock::new)
                .toArray(Goal[]::new);
        return new GoalAnd(new GoalComposite(goals), new GoalYLevel(gateway.getY() - 1));
    }

    private BlockPos nearestThrowPad(AltoClef mod, BlockPos gateway) {
        if (mod.getPlayer() == null) return null;
        BlockPos best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (BlockPos pad : throwPads(gateway)) {
            double dist = pad.distSqr(mod.getPlayer().blockPosition());
            if (dist < bestDist) {
                bestDist = dist;
                best = pad;
            }
        }
        return best;
    }

    private List<BlockPos> throwPads(BlockPos gateway) {
        List<BlockPos> result = new java.util.ArrayList<>();
        for (Vec3i offset : THROW_OFFSETS) {
            result.add(gateway.offset(offset));
        }
        return result;
    }
}
