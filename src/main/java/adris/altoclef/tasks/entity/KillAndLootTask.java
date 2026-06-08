package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

public class KillAndLootTask extends Task {
    private final String entityId;
    private final ItemTarget loot;
    private int commandCooldown;
    private int attackCooldown;

    public KillAndLootTask(String entityId, ItemTarget loot) {
        this.entityId = entityId.contains(":") ? entityId : "minecraft:" + entityId;
        this.loot = loot;
    }

    @Override
    protected void onStart(AltoClef mod) {
        commandCooldown = 0;
        attackCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (isFinished(mod)) return null;
        ItemEntity drop = mod.getEntityTracker().getClosestItemDrop(loot).orElse(null);
        if (drop != null) {
            gotoPos(mod, drop.blockPosition().getX(), drop.blockPosition().getY(), drop.blockPosition().getZ(), "Pickup " + loot);
            return null;
        }
        Entity entity = nearestEntity();
        if (entity == null) {
            setDebugState("No visible " + entityId);
            return null;
        }
        double reach = mod.getModSettings().getEntityReachRange();
        double distSqr = entity.distanceToSqr(mod.getPlayer());
        if (distSqr > reach * reach) {
            gotoPos(mod, entity.blockPosition().getX(), entity.blockPosition().getY(), entity.blockPosition().getZ(), "Move to " + entityId);
            return null;
        }
        mod.stopPathing();
        LookHelper.lookAt(mod, entity.getEyePosition());
        if (attackCooldown-- <= 0 && Minecraft.getInstance().gameMode != null && mod.getPlayer() != null) {
            attackCooldown = 10;
            Minecraft.getInstance().gameMode.attack(mod.getPlayer(), entity);
            mod.getPlayer().swing(InteractionHand.MAIN_HAND);
        }
        setDebugState("Kill " + entityId);
        return null;
    }

    private void gotoPos(AltoClef mod, int x, int y, int z, String state) {
        if (commandCooldown-- <= 0) {
            commandCooldown = 20;
            mod.runBaritone("goto " + x + " " + y + " " + z);
        }
        setDebugState(state);
    }

    private Entity nearestEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        Entity best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null || !entity.isAlive() || entity.isRemoved() || entity.equals(mc.player)) continue;
            if (id != null && id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
                double dist = entity.distanceToSqr(mc.player);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = entity;
                }
            }
        }
        return best;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.itemTargetsMetInventory(mod, loot);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof KillAndLootTask task && task.entityId.equals(entityId) && task.loot.equals(loot);
    }

    @Override
    protected String toDebugString() {
        return "Kill " + entityId + " for " + loot;
    }
}
