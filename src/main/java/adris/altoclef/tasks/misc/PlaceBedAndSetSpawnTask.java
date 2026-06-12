package adris.altoclef.tasks.misc;

import net.minecraft.core.BlockPos;

public class PlaceBedAndSetSpawnTask extends SleepThroughNightTask {
    public boolean isSpawnSet() {
        return false;
    }

    public BlockPos getBedSleptPos() {
        return null;
    }
}
