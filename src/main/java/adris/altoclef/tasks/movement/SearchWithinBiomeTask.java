package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public class SearchWithinBiomeTask extends TimeoutWanderTask {
    private final ResourceKey<Biome> biome;

    public SearchWithinBiomeTask(ResourceKey<Biome> biome) {
        super(true);
        this.biome = biome;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SearchWithinBiomeTask task && task.biome.equals(biome);
    }

    @Override
    protected String toDebugString() {
        return "Search biome " + biome.location();
    }
}
