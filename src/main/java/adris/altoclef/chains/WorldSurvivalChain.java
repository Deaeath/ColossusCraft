package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.DoToClosestBlockTask;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.construction.PutOutFireTask;
import adris.altoclef.tasks.movement.EnterNetherPortalTask;
import adris.altoclef.tasks.movement.EscapeFromLavaTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.SafeRandomShimmyTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

public class WorldSurvivalChain extends SingleTaskChain {

    private final TimerGame _wasInLavaTimer = new TimerGame(1);
    private boolean _wasAvoidingDrowning;
    private final TimerGame _portalStuckTimer = new TimerGame(5);

    // Partial-block stuck detection
    private Vec3 _lastStuckCheckPos = null;
    private final TimerGame _stuckTimer = new TimerGame(3);
    private final TimerGame _stuckCooldown = new TimerGame(5);

    private BlockPos _extinguishWaterPosition;

    public WorldSurvivalChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {
        mainTask = null;
    }

    @Override
    public float getPriority(AltoClef mod) {
        if (!AltoClef.inGame()) return Float.NEGATIVE_INFINITY;

        // Drowning
        handleDrowning(mod);

        // Lava Escape
        if (isInLavaOhShit(mod) && mod.getBehaviour().shouldEscapeLava()) {
            setTask(new EscapeFromLavaTask());
            return 100;
        }

        // Fire escape
        if (isInFire(mod)) {
            setTask(new DoToClosestBlockTask(PutOutFireTask::new, Blocks.FIRE, Blocks.SOUL_FIRE));
            return 100;
        }

        // Extinguish with water
        if (mod.getModSettings().shouldExtinguishSelfWithWater()) {
            if (!(mainTask instanceof EscapeFromLavaTask) && mod.getPlayer().isOnFire() && !mod.getPlayer().hasEffect(MobEffects.FIRE_RESISTANCE) && !mod.getWorld().dimensionType().ultraWarm()) {
                // Extinguish ourselves
                if (mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
                    BlockPos targetWaterPos = mod.getPlayer().blockPosition();
                    if (WorldHelper.isSolid(mod, targetWaterPos.below()) && WorldHelper.canPlace(mod, targetWaterPos)) {
                        Optional<Rotation> reach = LookHelper.getReach(targetWaterPos.below(), Direction.UP);
                        if (reach.isPresent()) {
                            mod.getClientBaritone().getLookBehavior().updateTarget(reach.get(), true);
                            if (mod.getClientBaritone().getPlayerContext().isLookingAt(targetWaterPos.below())) {
                                if (mod.getSlotHandler().forceEquipItem(Items.WATER_BUCKET)) {
                                    _extinguishWaterPosition = targetWaterPos;
                                    mod.getInputControls().tryPress(Input.CLICK_RIGHT);
                                    setTask(null);
                                    return 90;
                                }
                            }
                        }
                    }
                }
                setTask(new DoToClosestBlockTask(GetToBlockTask::new, Blocks.WATER));
                return 90;
            } else if (mod.getItemStorage().hasItem(Items.BUCKET) && _extinguishWaterPosition != null && mod.getBlockTracker().blockIsValid(_extinguishWaterPosition, Blocks.WATER)) {
                // Pick up the water
                setTask(new InteractWithBlockTask(new ItemTarget(Items.BUCKET, 1), Direction.UP, _extinguishWaterPosition.below(), true));
                return 60;
            } else {
                _extinguishWaterPosition = null;
            }
        }

        // Portal stuck
        if (isStuckInNetherPortal(mod)) {
            mod.getExtraBaritoneSettings().setInteractionPaused(true);
        } else {
            _portalStuckTimer.reset();
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
        }
        if (_portalStuckTimer.elapsed()) {
            setTask(new SafeRandomShimmyTask());
            return 60;
        }

        // Partial-block stuck: if Baritone is trying to path but player hasn't moved for 3s,
        // find any adjacent block with a collision shape and break it to clear the path.
        if (mod.getPlayer() != null && !_stuckCooldown.elapsed()) {
            _stuckTimer.reset();
            _lastStuckCheckPos = null;
        } else if (mod.getPlayer() != null) {
            boolean isNavigating = mod.getClientBaritone().getPathingBehavior().isPathing()
                    || mod.getClientBaritone().getCustomGoalProcess().isActive();
            if (isNavigating) {
                Vec3 pos = mod.getPlayer().position();
                if (_lastStuckCheckPos == null || pos.distanceToSqr(_lastStuckCheckPos) > 0.01) {
                    _stuckTimer.reset();
                    _lastStuckCheckPos = pos;
                }
                if (_stuckTimer.elapsed()) {
                    BlockPos playerPos = mod.getPlayer().blockPosition();
                    BlockPos toBreak = findAdjacentBlockingBlock(mod, playerPos);
                    if (toBreak != null) {
                        setTask(new DestroyBlockTask(toBreak));
                        _stuckTimer.reset();
                        _stuckCooldown.reset();
                        return 50;
                    }
                }
            } else {
                _stuckTimer.reset();
                _lastStuckCheckPos = null;
            }
        }

        return Float.NEGATIVE_INFINITY;
    }

    // Scans the blocks immediately surrounding the player (horizontal faces at feet and head
    // level, plus directly above) for anything with a non-empty collision shape that could be
    // physically blocking movement.
    private BlockPos findAdjacentBlockingBlock(AltoClef mod, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos[] candidates = {
            // Horizontal at feet
            feet.north(), feet.south(), feet.east(), feet.west(),
            // Horizontal at head
            head.north(), head.south(), head.east(), head.west(),
            // Directly above head (low-ceiling block)
            head.above(),
        };
        for (BlockPos candidate : candidates) {
            if (mod.getWorld().getBlockState(candidate).isAir()) continue;
            VoxelShape shape = mod.getWorld().getBlockState(candidate)
                    .getCollisionShape(mod.getWorld(), candidate);
            if (!shape.isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private void handleDrowning(AltoClef mod) {
        boolean avoidedDrowning = false;
        if (mod.getModSettings().shouldAvoidDrowning()) {
            if (!mod.getClientBaritone().getPathingBehavior().isPathing()) {
                if (mod.getPlayer().isInWater() && mod.getPlayer().getAirSupply() < mod.getPlayer().getMaxAirSupply()) {
                    // Swim up!
                    mod.getInputControls().hold(Input.JUMP);
                    avoidedDrowning = true;
                    _wasAvoidingDrowning = true;
                }
            }
        }
        if (_wasAvoidingDrowning && !avoidedDrowning) {
            _wasAvoidingDrowning = false;
            mod.getInputControls().release(Input.JUMP);
        }
    }

    private boolean isInLavaOhShit(AltoClef mod) {
        if (mod.getPlayer().isInLava() && !mod.getPlayer().hasEffect(MobEffects.FIRE_RESISTANCE)) {
            _wasInLavaTimer.reset();
            return true;
        }
        return mod.getPlayer().isOnFire() && !_wasInLavaTimer.elapsed();
    }

    private boolean isInFire(AltoClef mod) {
        if (mod.getPlayer().isOnFire() && !mod.getPlayer().hasEffect(MobEffects.FIRE_RESISTANCE)) {
            for (BlockPos pos : WorldHelper.getBlocksTouchingPlayer(mod)) {
                Block b = mod.getWorld().getBlockState(pos).getBlock();
                if (b instanceof BaseFireBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isStuckInNetherPortal(AltoClef mod) {
        if (!WorldHelper.isInNetherPortal(mod)) return false;
        Task current = mod.getUserTaskChain().getCurrentTask();
        if (current == null) return true;
        return !current.thisOrChildSatisfies(task -> task instanceof EnterNetherPortalTask);
    }

    @Override
    public String getName() {
        return "Misc World Survival Chain";
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public boolean isPassive() {
        return true;
    }

    @Override
    public boolean pausesBaritone() {
        return mainTask != null;
    }
}
