package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Spirals outward from the player's starting position until standing in the target biome.
 * Biome detection is client-side via loaded chunks — chunks load naturally as the bot walks.
 */
public class GoToBiomeTask extends Task {

    private final ResourceKey<Biome> _target;
    private SpiralSearchTask _spiral;

    public GoToBiomeTask(ResourceKey<Biome> target) {
        _target = target;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return false;
        return mod.getWorld().getBiome(mod.getPlayer().blockPosition()).is(_target);
    }

    @Override
    protected void onStart(AltoClef mod) {
        _spiral = null;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (_spiral == null) {
            BlockPos origin = mod.getPlayer().blockPosition();
            _spiral = new SpiralSearchTask(origin, _target);
        }
        setDebugState("Searching for biome: " + _target.location().getPath());
        return _spiral;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GoToBiomeTask t && t._target.equals(_target);
    }

    @Override
    protected String toDebugString() {
        return "Go to biome " + _target.location().getPath();
    }
}
