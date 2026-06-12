package adris.altoclef.util.helpers;

import adris.altoclef.util.Dimension;
import adris.altoclef.AltoClef;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

    public static int getTicks() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : (int) mc.level.getGameTime();
    }

    public static Iterable<BlockPos> getBlocksTouchingPlayer(AltoClef mod) {
        return getBlocksTouchingBox(mod, mod.getPlayer().getBoundingBox());
    }

    public static Iterable<BlockPos> getBlocksTouchingBox(AltoClef mod, AABB box) {
        BlockPos min = new BlockPos((int) box.minX, (int) box.minY, (int) box.minZ);
        BlockPos max = new BlockPos((int) box.maxX, (int) box.maxY, (int) box.maxZ);
        return scanRegion(mod, min, max);
    }

    public static boolean isBlock(AltoClef mod, BlockPos pos, net.minecraft.world.level.block.Block block) {
        return mod.getWorld() != null && mod.getWorld().getBlockState(pos).is(block);
    }

    public static int getGroundHeight(AltoClef mod, int x, int z) {
        for (int y = 319; y >= -64; --y) {
            if (isSolid(mod, new BlockPos(x, y, z))) return y;
        }
        return -1;
    }

    public static int getGroundHeight(AltoClef mod, int x, int z, Block... groundBlocks) {
        if (mod.getWorld() == null) return -1;
        for (int y = 319; y >= -64; --y) {
            BlockPos check = new BlockPos(x, y, z);
            Block b = mod.getWorld().getBlockState(check).getBlock();
            for (Block g : groundBlocks) {
                if (b == g) return y;
            }
        }
        return -1;
    }

    public static boolean inRangeXZ(Player player, net.minecraft.core.Vec3i pos, double range) {
        if (player == null || pos == null) return false;
        double dx = player.getX() - (pos.getX() + 0.5);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dz * dz <= range * range;
    }

    public static boolean inRangeXZ(Vec3 a, Vec3 b, double range) {
        if (a == null || b == null) return false;
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz <= range * range;
    }

    public static net.minecraft.world.phys.Vec3 toVec3d(BlockPos pos) {
        if (pos == null) return null;
        return new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static boolean isAir(AltoClef mod, BlockPos pos) {
        return mod.getBlockTracker().blockIsValid(pos, net.minecraft.world.level.block.Blocks.AIR,
                net.minecraft.world.level.block.Blocks.CAVE_AIR, net.minecraft.world.level.block.Blocks.VOID_AIR);
    }

    public static boolean isAir(Block block) {
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
    }

    public static boolean isSourceBlock(AltoClef mod, BlockPos pos, boolean acceptWaterlogged) {
        if (mod.getWorld() == null || pos == null) return false;
        BlockState state = mod.getWorld().getBlockState(pos);
        return state.getFluidState().isSource();
    }

    public static boolean fallingBlockSafeToBreak(BlockPos pos) {
        return true;
    }

    public static Entity getSpawnerEntity(AltoClef mod, BlockPos pos) {
        if (mod.getWorld() == null || pos == null) return null;
        if (mod.getWorld().getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            return spawner.getSpawner().getOrCreateDisplayEntity(mod.getWorld(), pos);
        }
        return null;
    }

    public static boolean isUnopenedChest(AltoClef mod, BlockPos pos) {
        return isBlock(mod, pos, Blocks.CHEST);
    }

    public static BlockPos getBedHead(AltoClef mod, BlockPos pos) {
        return pos;
    }

    public static BlockPos getBedFoot(AltoClef mod, BlockPos pos) {
        return pos;
    }

    public static boolean canSleep() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && !mc.level.isDay();
    }

    public static boolean inRangeXZ(Player player, Vec3 pos, double range) {
        if (player == null || pos == null) return false;
        double dx = player.getX() - pos.x;
        double dz = player.getZ() - pos.z;
        return dx * dx + dz * dz <= range * range;
    }

    public static BlockPos getADesertTemple(AltoClef mod) {
        return null;
    }

    public static boolean isFallingBlock(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return mc.level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.FallingBlock;
    }

    public static boolean isSolid(AltoClef mod, BlockPos pos) {
        if (mod.getWorld() == null || pos == null) return false;
        BlockState state = mod.getWorld().getBlockState(pos);
        return state.blocksMotion() || state.isSolid();
    }

    public static boolean canReach(AltoClef mod, BlockPos pos) {
        return pos != null && !mod.getBlockTracker().unreachable(pos);
    }

    public static boolean canPlace(AltoClef mod, BlockPos pos) {
        return canReach(mod, pos);
    }

    public static boolean canBreak(AltoClef mod, BlockPos pos) {
        return pos != null && canReach(mod, pos) && mod.getWorld() != null
                && mod.getWorld().getBlockState(pos).getDestroySpeed(mod.getWorld(), pos) >= 0;
    }

    public static boolean isInNetherPortal(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return false;
        for (BlockPos pos : getBlocksTouchingPlayer(mod)) {
            if (mod.getWorld().getBlockState(pos).getBlock() == net.minecraft.world.level.block.Blocks.NETHER_PORTAL) {
                return true;
            }
        }
        return false;
    }
}
