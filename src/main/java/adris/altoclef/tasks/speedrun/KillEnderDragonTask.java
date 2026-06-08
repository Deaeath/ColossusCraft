package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.entity.KillAndLootTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.Items;

public class KillEnderDragonTask extends Task {
    private int attackCooldown;
    private int commandCooldown;

    @Override
    protected void onStart(AltoClef mod) {
        attackCooldown = 0;
        commandCooldown = 0;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Entity crystal = nearest("minecraft:end_crystal");
        if (crystal != null) {
            return attackEntity(mod, crystal, "Break end crystal");
        }
        EnderDragon dragon = nearestDragon();
        if (dragon == null) {
            setDebugState("No dragon visible");
            return null;
        }
        return attackEntity(mod, dragon, "Attack dragon");
    }

    private Task attackEntity(AltoClef mod, Entity entity, String state) {
        double reach = mod.getModSettings().getEntityReachRange();
        if (mod.getPlayer() != null && entity.distanceToSqr(mod.getPlayer()) > reach * reach) {
            if (commandCooldown-- <= 0) {
                commandCooldown = 20;
                mod.runBaritone("goto " + entity.blockPosition().getX() + " " + entity.blockPosition().getY() + " " + entity.blockPosition().getZ());
            }
            setDebugState("Move to " + state);
            return null;
        }
        mod.stopPathing();
        LookHelper.lookAt(mod, entity.getEyePosition());
        if (attackCooldown-- <= 0 && Minecraft.getInstance().gameMode != null && mod.getPlayer() != null) {
            attackCooldown = 10;
            Minecraft.getInstance().gameMode.attack(mod.getPlayer(), entity);
            mod.getPlayer().swing(InteractionHand.MAIN_HAND);
        }
        setDebugState(state);
        return null;
    }

    private EnderDragon nearestDragon() {
        Entity entity = nearest("minecraft:ender_dragon");
        return entity instanceof EnderDragon dragon ? dragon : null;
    }

    private Entity nearest(String idText) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(idText);
        Entity best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null || !entity.isAlive() || entity.isRemoved()) continue;
            if (id != null && id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
                double dist = entity.distanceToSqr(mc.player);
                if (dist < bestDist) {
                    best = entity;
                    bestDist = dist;
                }
            }
        }
        return best;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof KillEnderDragonTask;
    }

    @Override
    protected String toDebugString() {
        return "Kill Ender Dragon";
    }
}
