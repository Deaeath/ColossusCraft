package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.MLGBucketTask;
import adris.altoclef.tasksystem.ITaskOverridesGrounded;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

public class MLGBucketFallChain extends SingleTaskChain implements ITaskOverridesGrounded {

    private boolean _doingChorusFruit = false;
    private final TimerGame _recentMLGTimer = new TimerGame(1.5);

    public MLGBucketFallChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {
        mainTask = null;
    }

    @Override
    public float getPriority(AltoClef mod) {
        if (!AltoClef.inGame()) return Float.NEGATIVE_INFINITY;
        if (isFallingOhNo(mod) && (mainTask != null || mod.getItemStorage().hasItem(Items.WATER_BUCKET))) {
            _recentMLGTimer.reset();
            setTask(new MLGBucketTask());
            return 100;
        }
        if (mainTask != null) {
            return 100;
        }
        if (mod.getPlayer().hasEffect(MobEffects.LEVITATION) &&
                !mod.getPlayer().getCooldowns().isOnCooldown(Items.CHORUS_FRUIT) &&
                mod.getPlayer().getEffect(MobEffects.LEVITATION).getDuration() <= 70 &&
                mod.getItemStorage().hasItemInventoryOnly(Items.CHORUS_FRUIT) &&
                !mod.getItemStorage().hasItemInventoryOnly(Items.WATER_BUCKET)) {
            _doingChorusFruit = true;
            mod.getSlotHandler().forceEquipItem(Items.CHORUS_FRUIT);
            mod.getInputControls().hold(Input.CLICK_RIGHT);
            mod.getExtraBaritoneSettings().setInteractionPaused(true);
        } else if (_doingChorusFruit) {
            _doingChorusFruit = false;
            mod.getInputControls().release(Input.CLICK_RIGHT);
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
        }
        return Float.NEGATIVE_INFINITY;
    }

    @Override
    public String getName() {
        return "MLG Water Bucket Fall Chain";
    }

    @Override
    public boolean isActive() {
        // We're always checking for mlg.
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

    public boolean doneMLG(AltoClef mod) {
        return !isFallingOhNo(mod);
    }

    public boolean recentlyHandledMLG() {
        return !_recentMLGTimer.elapsed();
    }

    public boolean isChorusFruiting() {
        return _doingChorusFruit;
    }

    public boolean isFallingOhNo(AltoClef mod) {
        if (!mod.getModSettings().shouldAutoMLGBucket()) {
            return false;
        }
        if (mod.getPlayer() == null) return false;
        if (mod.getPlayer().isSwimming() || mod.getPlayer().isInWater() || mod.getPlayer().onGround() || mod.getPlayer().onClimbable()) {
            // We're grounded.
            return false;
        }
        double ySpeed = mod.getPlayer().getDeltaMovement().y;
        return ySpeed < -0.7;
    }
}
