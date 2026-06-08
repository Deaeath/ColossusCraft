package adris.altoclef.util.helpers;

import adris.altoclef.util.Dimension;
import adris.altoclef.AltoClef;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class WorldHelper {
    private WorldHelper() {
    }

    public static Dimension getCurrentDimension() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return Dimension.OVERWORLD;
        }
        ResourceLocation id = mc.level.dimension().location();
        if (id.getPath().equals("the_nether")) {
            return Dimension.NETHER;
        }
        if (id.getPath().equals("the_end")) {
            return Dimension.END;
        }
        return Dimension.OVERWORLD;
    }

    public static Iterable<BlockPos> scanRegion(AltoClef mod, BlockPos start, BlockPos end) {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(start, end)) {
            result.add(pos.immutable());
        }
        return result;
    }

    public static boolean isSolid(AltoClef mod, BlockPos pos) {
        if (mod.getWorld() == null || pos == null) return false;
        BlockState state = mod.getWorld().getBlockState(pos);
        return state.blocksMotion() || state.isSolid();
    }

    public static boolean canReach(AltoClef mod, BlockPos pos) {
        return pos != null && !mod.getBlockTracker().unreachable(pos);
    }
}
