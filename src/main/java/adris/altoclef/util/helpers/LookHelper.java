package adris.altoclef.util.helpers;

import adris.altoclef.AltoClef;
import baritone.api.BaritoneAPI;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class LookHelper {
    private LookHelper() {
    }

    public static void randomOrientation(AltoClef mod) {
        if (mod.getPlayer() != null) {
            mod.getPlayer().setYRot((float) (Math.random() * 360.0));
        }
    }

    public static Rotation getLookRotation() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return new Rotation(0, 0);
        return new Rotation(p.getYRot(), p.getXRot());
    }

    public static Vec3 toVec3d(Rotation rotation) {
        double yaw = Math.toRadians(rotation.getYaw());
        double pitch = Math.toRadians(rotation.getPitch());
        double x = -Math.cos(pitch) * Math.sin(yaw);
        double y = -Math.sin(pitch);
        double z = Math.cos(pitch) * Math.cos(yaw);
        return new Vec3(x, y, z);
    }

    public static void lookAt(AltoClef mod, Rotation rotation) {
        mod.getClientBaritone().getLookBehavior().updateTarget(rotation, true);
        if (mod.getPlayer() != null) {
            mod.getPlayer().setYRot(rotation.getYaw());
            mod.getPlayer().setXRot(rotation.getPitch());
        }
    }

    public static void lookAt(AltoClef mod, BlockPos pos, Direction side) {
        Vec3i n = side.getNormal();
        lookAt(mod, new Vec3(pos.getX() + 0.5 + n.getX() * 0.5, pos.getY() + 0.5 + n.getY() * 0.5, pos.getZ() + 0.5 + n.getZ() * 0.5));
    }

    public static Vec3 getCameraPos(AltoClef mod) {
        return mod.getPlayer() == null ? Vec3.ZERO : mod.getPlayer().getEyePosition();
    }

    public static boolean cleanLineOfSight(Entity entity, Vec3 end, double maxRange) {
        if (entity == null) return false;
        Vec3 start = entity.getEyePosition();
        if (start.distanceToSqr(end) > maxRange * maxRange) return false;
        return cleanLineOfSight(entity, start, end);
    }

    public static boolean cleanLineOfSight(Entity entity, BlockPos end, double maxRange) {
        return cleanLineOfSight(entity, Vec3.atCenterOf(end), maxRange);
    }

    public static Optional<Rotation> getReach(BlockPos target) {
        return getReach(target, null);
    }

    public static Optional<Rotation> getReach(BlockPos target, Direction side) {
        IPlayerContext ctx = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext();
        if (side == null) {
            return RotationUtils.reachable(ctx.player(), target, ctx.playerController().getBlockReachDistance());
        }
        Vec3i n = side.getNormal();
        Vec3 centerOffset = new Vec3(0.5 + n.getX() * 0.5, 0.5 + n.getY() * 0.5, 0.5 + n.getZ() * 0.5);
        Vec3 sidePoint = centerOffset.add(target.getX(), target.getY(), target.getZ());
        Optional<Rotation> reachable = RotationUtils.reachableOffset(ctx.player(), target, sidePoint, ctx.playerController().getBlockReachDistance(), false);
        if (reachable.isPresent()) {
            Vec3 camPos = ctx.player().getEyePosition(1.0F);
            Vec3 vecToPlayerPos = camPos.subtract(sidePoint);
            double dot = vecToPlayerPos.normalize().dot(new Vec3(n.getX(), n.getY(), n.getZ()));
            if (dot < 0) {
                return Optional.empty();
            }
        }
        return reachable;
    }

    public static boolean isLookingAt(AltoClef mod, BlockPos blockPos) {
        return mod.getClientBaritone().getPlayerContext().isLookingAt(blockPos);
    }

    public static boolean seesPlayer(Entity entity, Entity player, double maxRange) {
        Vec3 start = entity.getEyePosition();
        Vec3 end = player.getEyePosition();
        if (start.distanceToSqr(end) > maxRange * maxRange) return false;
        return cleanLineOfSight(entity, start, end) || cleanLineOfSight(entity, start, end.add(0, -1, 0));
    }

    private static boolean cleanLineOfSight(Entity entity, Vec3 start, Vec3 end) {
        HitResult hit = entity.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        return hit.getType() == HitResult.Type.MISS;
    }

    public static void lookAt(AltoClef mod, Vec3 target) {
        if (mod.getPlayer() == null) return;
        Vec3 eye = mod.getPlayer().getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double xz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, xz));
        mod.getInputControls().forceLook(yaw, pitch);
    }

    public static void lookAt(AltoClef mod, BlockPos pos) {
        lookAt(mod, pos.getCenter());
    }
}
