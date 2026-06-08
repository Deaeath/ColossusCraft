package adris.altoclef.trackers;

import adris.altoclef.AltoClef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public class SimpleChunkTracker {
    public void reset(AltoClef mod) {
    }

    public boolean isChunkLoaded(BlockPos pos) {
        return AltoClef.inGame() && pos != null && net.minecraft.client.Minecraft.getInstance().level.isLoaded(pos);
    }

    public boolean isChunkLoaded(ChunkPos pos) {
        if (!AltoClef.inGame() || pos == null) return false;
        return net.minecraft.client.Minecraft.getInstance().level.hasChunk(pos.x, pos.z);
    }
}
