package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.control.KillAura;
import adris.altoclef.tasksystem.Task;
import baritone.api.utils.input.Input;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Reactive self-defense: feed nearby hostiles into KillAura's force field, which auto-switches to
 * the best weapon, raises a shield between swings, and attacks on a recovered cooldown.
 */
public class DefendFromHostilesTask extends Task {

    private static final double DEFEND_RANGE_SQR = 7.0 * 7.0;

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (mod.getPlayer() == null) return null;
        // Swelling creeper close by: stop, raise shield, walk back out of the blast (don't melee it).
        for (Entity h : mod.getEntityTracker().getHostiles()) {
            if (h instanceof Creeper c && c.getSwelling(1.0f) > 0.0f && c.distanceToSqr(mod.getPlayer()) < 36.0) {
                mod.getKillAura().stopShielding(mod);
                mod.getInputControls().hold(Input.MOVE_BACK);
                if (mod.getItemStorage().hasItemInventoryOnly(net.minecraft.world.item.Items.SHIELD)) {
                    mod.getInputControls().hold(Input.CLICK_RIGHT);
                }
                setDebugState("Evade creeper");
                return null;
            }
        }
        mod.getInputControls().release(Input.MOVE_BACK);
        KillAura killAura = mod.getKillAura();
        killAura.tickStart();
        for (Entity hostile : mod.getEntityTracker().getHostiles()) {
            if (hostile.distanceToSqr(mod.getPlayer()) <= DEFEND_RANGE_SQR) {
                killAura.applyAura(hostile);
            }
        }
        killAura.tickEnd(mod);
        setDebugState("Defending");
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.getKillAura().stopShielding(mod);
        mod.getInputControls().release(Input.MOVE_BACK);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof DefendFromHostilesTask;
    }

    @Override
    protected String toDebugString() {
        return "Defend from hostiles";
    }
}
