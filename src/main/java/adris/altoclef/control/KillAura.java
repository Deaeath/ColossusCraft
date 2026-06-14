package adris.altoclef.control;

import adris.altoclef.AltoClef;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StlHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controls and applies killaura. Ported from upstream AltoClef (1.19.4) to NeoForge/MojMap 1.21.1.
 */
public class KillAura {
    // Smart aura data
    private final List<Entity> _targets = new ArrayList<>();
    private final TimerGame _hitDelay = new TimerGame(0.2);
    boolean _shielding = false;
    private double _forceFieldRange = Double.POSITIVE_INFINITY;
    private Entity _forceHit = null;

    private static double weaponScore(ItemStack stack) {
        Item item = stack.getItem();
        // Prefer swords, then axes; rank by tier via the item's base attack damage component.
        double base = 0;
        var mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods != null) {
            for (var entry : mods.modifiers()) {
                if (entry.attribute() == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) {
                    base += entry.modifier().amount();
                }
            }
        }
        if (item instanceof SwordItem) return base + 100; // swords win ties (sweep + faster recovery)
        if (item instanceof AxeItem) return base + 50;
        return Double.NEGATIVE_INFINITY;
    }

    public static void equipWeapon(AltoClef mod) {
        List<ItemStack> invStacks = mod.getItemStorage().getItemStacksPlayerInventory(true);
        if (invStacks.isEmpty()) return;
        ItemStack best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (ItemStack invStack : invStacks) {
            double score = weaponScore(invStack);
            if (score > bestScore) {
                bestScore = score;
                best = invStack;
            }
        }
        if (best != null && bestScore > Double.NEGATIVE_INFINITY) {
            ItemStack hand = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot());
            if (weaponScore(hand) < bestScore) {
                mod.getSlotHandler().forceEquipItem(best.getItem());
            }
        }
    }

    public void tickStart() {
        _targets.clear();
        _forceHit = null;
    }

    public void applyAura(Entity entity) {
        _targets.add(entity);
        if (isDeflectableProjectile(entity)) _forceHit = entity;
    }

    public static boolean isDeflectableProjectile(Entity entity) {
        return (entity instanceof Fireball && !(entity instanceof DragonFireball))
                || entity instanceof WitherSkull
                || entity instanceof AbstractWindCharge;
    }

    public void setRange(double range) {
        _forceFieldRange = range;
    }

    public void tickEnd(AltoClef mod) {
        Item offhandItem = StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT).getItem();
        Optional<Entity> entities = _targets.stream().min(StlHelper.compareValues(entity -> entity.distanceToSqr(mod.getPlayer())));
        if (entities.isPresent() && mod.getPlayer().getHealth() >= 10 &&
                !mod.getEntityTracker().entityFound(ThrownPotion.class) && !mod.getFoodChain().needsToEat() &&
                (mod.getItemStorage().hasItem(Items.SHIELD) || mod.getItemStorage().hasItemInOffhand(Items.SHIELD)) &&
                (Double.isInfinite(_forceFieldRange) || entities.get().distanceToSqr(mod.getPlayer()) < _forceFieldRange * _forceFieldRange ||
                        entities.get().distanceToSqr(mod.getPlayer()) < 40) &&
                !mod.getMLGBucketChain().isFallingOhNo(mod) && mod.getMLGBucketChain().doneMLG(mod) &&
                !mod.getMLGBucketChain().isChorusFruiting() &&
                !mod.getPlayer().getCooldowns().isOnCooldown(offhandItem)) {
            if (entities.get() instanceof Monster &&
                    entities.get().getClass() != Creeper.class && entities.get().getClass() != Hoglin.class &&
                    entities.get().getClass() != Zoglin.class && entities.get().getClass() != Warden.class &&
                    entities.get().getClass() != WitherBoss.class) {
                LookHelper.lookAt(mod, entities.get().getEyePosition());
                ItemStack shieldSlot = StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT);
                if (shieldSlot.getItem() != Items.SHIELD) {
                    mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
                } else {
                    startShielding(mod);
                    performDelayedAttack(mod);
                    return;
                }
            }
        } else {
            stopShielding(mod);
        }
        // Run force field on map
        switch (mod.getModSettings().getForceFieldStrategy()) {
            case FASTEST:
                performFastestAttack(mod);
                break;
            case SMART:
                if (_targets.size() <= 2 || _targets.stream().allMatch(entity -> entity instanceof Skeleton) ||
                        _targets.stream().allMatch(entity -> entity instanceof Witch) ||
                        _targets.stream().allMatch(entity -> entity instanceof Pillager) ||
                        _targets.stream().allMatch(entity -> entity instanceof Piglin) ||
                        _targets.stream().allMatch(entity -> entity instanceof Stray) ||
                        _targets.stream().allMatch(entity -> entity instanceof Blaze)) {
                    performDelayedAttack(mod);
                } else {
                    if (!mod.getFoodChain().needsToEat() && !mod.getMLGBucketChain().isFallingOhNo(mod) &&
                            mod.getMLGBucketChain().doneMLG(mod) && !mod.getMLGBucketChain().isChorusFruiting()) {
                        // Attack force mobs ALWAYS.
                        if (_forceHit != null) {
                            attack(mod, _forceHit, true);
                        }
                        if (_hitDelay.elapsed()) {
                            _hitDelay.reset();
                            Optional<Entity> toHit = _targets.stream().min(StlHelper.compareValues(entity -> entity.distanceToSqr(mod.getPlayer())));
                            toHit.ifPresent(entity -> attack(mod, entity, true));
                        }
                    }
                }
                break;
            case DELAY:
                performDelayedAttack(mod);
                break;
            case OFF:
                break;
        }
    }

    private void performDelayedAttack(AltoClef mod) {
        if (!mod.getFoodChain().needsToEat() && !mod.getMLGBucketChain().isFallingOhNo(mod) &&
                mod.getMLGBucketChain().doneMLG(mod) && !mod.getMLGBucketChain().isChorusFruiting()) {
            if (_forceHit != null) {
                attack(mod, _forceHit, true);
            }
            if (_targets.isEmpty()) {
                return;
            }
            Optional<Entity> toHit = _targets.stream().min(StlHelper.compareValues(entity -> entity.distanceToSqr(mod.getPlayer())));
            if (mod.getPlayer() == null || mod.getPlayer().getAttackStrengthScale(0) < 1) {
                return;
            }
            toHit.ifPresent(entity -> attack(mod, entity, true));
        }
    }

    private void performFastestAttack(AltoClef mod) {
        if (!mod.getFoodChain().needsToEat() && !mod.getMLGBucketChain().isFallingOhNo(mod) &&
                mod.getMLGBucketChain().doneMLG(mod) && !mod.getMLGBucketChain().isChorusFruiting()) {
            for (Entity entity : _targets) {
                attack(mod, entity);
            }
        }
    }

    private void attack(AltoClef mod, Entity entity) {
        attack(mod, entity, false);
    }

    private void attack(AltoClef mod, Entity entity, boolean equipSword) {
        if (entity == null) return;
        LookHelper.lookAt(mod, entity.getEyePosition());
        if (Double.isInfinite(_forceFieldRange) || entity.distanceToSqr(mod.getPlayer()) < _forceFieldRange * _forceFieldRange ||
                entity.distanceToSqr(mod.getPlayer()) < 40) {
            if (isDeflectableProjectile(entity)) {
                mod.getControllerExtras().attack(entity);
                return;
            }
            boolean canAttack;
            if (equipSword) {
                equipWeapon(mod);
                canAttack = true;
            } else {
                canAttack = mod.getSlotHandler().forceDeequipHitTool();
            }
            if (canAttack) {
                if (mod.getPlayer().onGround() || mod.getPlayer().getDeltaMovement().y < 0 || mod.getPlayer().isInWater()) {
                    mod.getControllerExtras().attack(entity);
                }
            }
        }
    }

    public void startShielding(AltoClef mod) {
        _shielding = true;
        mod.getInputControls().hold(Input.SNEAK);
        mod.getInputControls().hold(Input.CLICK_RIGHT);
        mod.getExtraBaritoneSettings().setInteractionPaused(true);
        if (!mod.getPlayer().isBlocking()) {
            ItemStack handItem = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot());
            if (handItem.has(DataComponents.FOOD)) {
                List<ItemStack> spaceSlots = mod.getItemStorage().getItemStacksPlayerInventory(false);
                if (!spaceSlots.isEmpty()) {
                    for (ItemStack spaceSlot : spaceSlots) {
                        if (spaceSlot.isEmpty()) {
                            mod.getSlotHandler().clickSlot(PlayerSlot.getEquipSlot(), 0, ClickType.QUICK_MOVE);
                            return;
                        }
                    }
                }
                Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
                garbage.ifPresent(slot -> mod.getSlotHandler().forceEquipItem(StorageHelper.getItemStackInSlot(slot).getItem()));
            }
        }
    }

    public void stopShielding(AltoClef mod) {
        if (_shielding) {
            ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
            if (cursor.has(DataComponents.FOOD)) {
                Optional<Slot> toMoveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false).or(() -> StorageHelper.getGarbageSlot(mod));
                if (toMoveTo.isPresent()) {
                    Slot garbageSlot = toMoveTo.get();
                    mod.getSlotHandler().clickSlot(garbageSlot, 0, ClickType.PICKUP);
                }
            }
            mod.getInputControls().release(Input.SNEAK);
            mod.getInputControls().release(Input.CLICK_RIGHT);
            mod.getInputControls().release(Input.JUMP);
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
            _shielding = false;
        }
    }

    public enum Strategy {
        OFF,
        FASTEST,
        DELAY,
        SMART
    }
}
