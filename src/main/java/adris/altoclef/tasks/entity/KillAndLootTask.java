package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.item.Items;

public class KillAndLootTask extends ResourceTask {
    private final String entityId;
    private final ItemTarget loot;
    private int commandCooldown;
    private boolean pushedBehaviour;

    public KillAndLootTask(String entityId, ItemTarget loot) {
        super(loot);
        this.entityId = entityId.contains(":") ? entityId : "minecraft:" + entityId;
        this.loot = loot;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {
        commandCooldown = 0;
        pushedBehaviour = false;
        if (isWitherSkullTask()) {
            mod.getBehaviour().push();
            mod.getBehaviour().addForceFieldExclusion(entity -> entity instanceof Blaze);
            pushedBehaviour = true;
        }
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        if (isFinished(mod)) return null;
        ItemEntity drop = mod.getEntityTracker().getClosestItemDrop(loot).orElse(null);
        if (drop != null) {
            mod.getMobDefenseChain().resetTargetEntity();
            mod.getKillAura().stopShielding(mod);
            gotoPos(mod, drop.blockPosition().getX(), drop.blockPosition().getY(), drop.blockPosition().getZ(), "Pickup " + loot);
            return null;
        }
        Entity entity = nearestEntity();
        if (entity == null) {
            mod.getMobDefenseChain().resetTargetEntity();
            mod.getKillAura().stopShielding(mod);
            setDebugState("No visible " + entityId);
            return null;
        }
        mod.getMobDefenseChain().setTargetEntity(entity);
        double reach = mod.getModSettings().getEntityReachRange();
        double distSqr = entity.distanceToSqr(mod.getPlayer());
        if (distSqr > reach * reach) {
            mod.getKillAura().stopShielding(mod);
            gotoPos(mod, entity.blockPosition().getX(), entity.blockPosition().getY(), entity.blockPosition().getZ(), "Move to " + entityId);
            return null;
        }
        mod.stopPathing();
        adris.altoclef.control.KillAura killAura = mod.getKillAura();
        killAura.tickStart();
        killAura.applyAura(entity);
        killAura.tickEnd(mod);
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

    private Entity _lockedTarget;

    private boolean isMatch(Entity entity, ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == null || !entity.isAlive() || entity.isRemoved() || entity.equals(mc.player)) return false;
        return id != null && id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    private Entity nearestEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        // Stay locked on the current target until it dies/leaves, so we don't flip-flop between
        // multiple nearby mobs (which never lets us finish a kill and reach the drop-pickup branch).
        if (isMatch(_lockedTarget, id)) {
            return _lockedTarget;
        }
        Entity best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!isMatch(entity, id)) continue;
            double dist = entity.distanceToSqr(mc.player);
            if (dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        _lockedTarget = best;
        return best;
    }

    public boolean isWitherSkullTask() {
        return entityId.equals("minecraft:wither_skeleton") && loot.matches(Items.WITHER_SKELETON_SKULL);
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return StorageHelper.itemTargetsMetInventory(mod, loot);
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
        mod.getMobDefenseChain().resetTargetEntity();
        mod.getMobDefenseChain().resetForceField();
        mod.getKillAura().stopShielding(mod);
        mod.stopPathing();
        if (pushedBehaviour) {
            mod.getBehaviour().pop();
            pushedBehaviour = false;
        }
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
