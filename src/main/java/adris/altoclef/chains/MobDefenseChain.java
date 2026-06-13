package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.control.KillAura;
import adris.altoclef.tasks.entity.KillAndLootTask;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.DodgeProjectilesTask;
import adris.altoclef.tasks.movement.RunAwayFromCreepersTask;
import adris.altoclef.tasks.movement.RunAwayFromHostilesTask;
import adris.altoclef.tasks.speedrun.DragonBreathTracker;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.baritone.CachedProjectile;
import adris.altoclef.util.helpers.BaritoneHelper;
import adris.altoclef.util.helpers.EntityHelper;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.ProjectileHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class MobDefenseChain extends SingleTaskChain {
    private static final double DANGER_KEEP_DISTANCE = 30;
    private static final double CREEPER_KEEP_DISTANCE = 10;
    private static final double ARROW_KEEP_DISTANCE_HORIZONTAL = 2;
    private static final double ARROW_KEEP_DISTANCE_VERTICAL = 10;
    private static final double SAFE_KEEP_DISTANCE = 8;
    private final DragonBreathTracker _dragonBreathTracker = new DragonBreathTracker();
    private final KillAura _killAura = new KillAura();
    private final HashMap<Entity, TimerGame> _closeAnnoyingEntities = new HashMap<>();
    private Entity _targetEntity;
    private boolean _shielding = false;
    private boolean _doingFunkyStuff = false;
    private boolean _wasPuttingOutFire = false;
    private Task _runAwayTask;

    private float _cachedLastPriority;

    public MobDefenseChain(TaskRunner runner) {
        super(runner);
    }

    public static double getCreeperSafety(Vec3 pos, Creeper creeper) {
        double distance = creeper.distanceToSqr(pos);
        float fuse = creeper.getSwelling(1.0f);

        // Not fusing.
        if (fuse <= 0.001f) return distance;
        return distance * 0.2; // less is WORSE
    }

    @Override
    public float getPriority(AltoClef mod) {
        _cachedLastPriority = getPriorityInner(mod);
        return _cachedLastPriority;
    }

    private void startShielding(AltoClef mod) {
        _shielding = true;
        mod.getInputControls().hold(Input.SNEAK);
        mod.getInputControls().hold(Input.CLICK_RIGHT);
        mod.getClientBaritone().getPathingBehavior().cancelEverything();
        mod.getExtraBaritoneSettings().setInteractionPaused(true);
        if (!mod.getPlayer().isBlocking()) {
            ItemStack handItem = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot());
            if (handItem.has(net.minecraft.core.component.DataComponents.FOOD)) {
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

    private void stopShielding(AltoClef mod) {
        if (_shielding) {
            ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
            if (cursor.has(net.minecraft.core.component.DataComponents.FOOD)) {
                Optional<Slot> toMoveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false).or(() -> StorageHelper.getGarbageSlot(mod));
                if (toMoveTo.isPresent()) {
                    Slot garbageSlot = toMoveTo.get();
                    mod.getSlotHandler().clickSlot(garbageSlot, 0, ClickType.PICKUP);
                }
            }
            mod.getInputControls().release(Input.SNEAK);
            mod.getInputControls().release(Input.CLICK_RIGHT);
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
            _shielding = false;
        }
    }

    private boolean escapeDragonBreath(AltoClef mod) {
        _dragonBreathTracker.updateBreath(mod);
        for (BlockPos playerIn : WorldHelper.getBlocksTouchingPlayer(mod)) {
            if (_dragonBreathTracker.isTouchingDragonBreath(playerIn)) {
                return true;
            }
        }
        return false;
    }

    public float getPriorityInner(AltoClef mod) {
        if (!AltoClef.inGame()) {
            return Float.NEGATIVE_INFINITY;
        }

        if (!mod.getModSettings().isMobDefense()) {
            return Float.NEGATIVE_INFINITY;
        }

        // Put out fire if we're standing on one like an idiot
        BlockPos fireBlock = isInsideFireAndOnFire(mod);
        if (fireBlock != null) {
            putOutFire(mod, fireBlock);
            _wasPuttingOutFire = true;
        } else if (_wasPuttingOutFire) {
            // Only release the left-click WE pressed for fire — don't stomp the player's manual mining.
            mod.getInputControls().release(Input.CLICK_LEFT);
            _wasPuttingOutFire = false;
        }

        if (mod.getFoodChain().needsToEat() || mod.getMLGBucketChain().isFallingOhNo(mod) ||
                !mod.getMLGBucketChain().doneMLG() || mod.getMLGBucketChain().isChorusFruiting()) {
            _killAura.stopShielding(mod);
            stopShielding(mod);
            return Float.NEGATIVE_INFINITY;
        }

        boolean userTaskHasPriority = mod.getUserTaskChain().shouldPrioritizeOverMobDefense();

        if (hitCloseShulkerBullet(mod)) {
            _doingFunkyStuff = true;
            setTask(null);
            return 99;
        }

        // Force field
        if (userTaskHasPriority) {
            doEmergencyForceField(mod);
            return getEmergencyPriorityForUserTask(mod);
        }
        doForceField(mod);

        // Run away if a weird mob is close by.
        Optional<Entity> universallyDangerous = getUniversallyDangerousMob(mod);
        if (universallyDangerous.isPresent()) {
            _runAwayTask = new RunAwayFromHostilesTask(DANGER_KEEP_DISTANCE, true);
            setTask(_runAwayTask);
            return 70;
        }

        _doingFunkyStuff = false;
        // Run away from creepers
        Creeper blowingUp = getClosestFusingCreeper(mod);
        if (blowingUp != null) {
            if (!mod.getFoodChain().needsToEat() && (mod.getItemStorage().hasItem(Items.SHIELD) ||
                    mod.getItemStorage().hasItemInOffhand(Items.SHIELD)) &&
                    !mod.getEntityTracker().entityFound(ThrownPotion.class) && _runAwayTask == null) {
                _doingFunkyStuff = true;
                LookHelper.lookAt(mod, blowingUp.getEyePosition());
                ItemStack shieldSlot = StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT);
                if (shieldSlot.getItem() != Items.SHIELD) {
                    mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
                } else {
                    startShielding(mod);
                }
            } else {
                _doingFunkyStuff = true;
                _runAwayTask = new RunAwayFromCreepersTask(CREEPER_KEEP_DISTANCE);
                setTask(_runAwayTask);
                return 50 + blowingUp.getSwelling(1.0f) * 50;
            }
        } else {
            if (!isProjectileClose(mod)) {
                stopShielding(mod);
            }
        }
        // Block projectiles with shield
        Item offhandItem = StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT).getItem();
        if (!mod.getFoodChain().needsToEat() && mod.getModSettings().isDodgeProjectiles() && isProjectileClose(mod) &&
                (mod.getItemStorage().hasItem(Items.SHIELD) || mod.getItemStorage().hasItemInOffhand(Items.SHIELD)) &&
                !mod.getEntityTracker().entityFound(ThrownPotion.class) && _runAwayTask == null &&
                !mod.getPlayer().getCooldowns().isOnCooldown(offhandItem)) {
            ItemStack shieldSlot = StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT);
            if (shieldSlot.getItem() != Items.SHIELD) {
                mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
            } else {
                startShielding(mod);
            }
        } else {
            if (blowingUp == null) {
                stopShielding(mod);
            }
        }
        // Dodge projectiles
        if (mod.getPlayer().getHealth() <= 10 || _runAwayTask != null || mod.getEntityTracker().entityFound(ThrownPotion.class) ||
                (!mod.getItemStorage().hasItem(Items.SHIELD) && !mod.getItemStorage().hasItemInOffhand(Items.SHIELD))) {
            if (!mod.getFoodChain().needsToEat() && mod.getModSettings().isDodgeProjectiles() && isProjectileClose(mod)) {
                _doingFunkyStuff = true;
                _runAwayTask = new DodgeProjectilesTask(ARROW_KEEP_DISTANCE_HORIZONTAL, ARROW_KEEP_DISTANCE_VERTICAL);
                setTask(_runAwayTask);
                return 65;
            }
        }
        // Dodge all mobs cause we boutta die son
        if (isInDanger(mod) && !escapeDragonBreath(mod) && !mod.getFoodChain().isShouldStop()) {
            if (_targetEntity == null) {
                _runAwayTask = new RunAwayFromHostilesTask(DANGER_KEEP_DISTANCE, true);
                setTask(_runAwayTask);
                return 70;
            }
        }

        if (mod.getModSettings().shouldDealWithAnnoyingHostiles()) {
            // Deal with hostiles because they are annoying.
            List<Entity> hostiles = mod.getEntityTracker().getHostiles();

            Item bestSword = null;
            Item[] SWORDS = new Item[]{Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD,
                    Items.STONE_SWORD, Items.WOODEN_SWORD};
            for (Item item : SWORDS) {
                if (mod.getItemStorage().hasItem(item)) {
                    bestSword = item;
                }
            }

            List<Entity> toDealWith = new ArrayList<>();

            if (!hostiles.isEmpty()) {
                synchronized (BaritoneHelper.MINECRAFT_LOCK) {
                    for (Entity hostile : hostiles) {
                        if (shouldIgnoreBlazeForSkulls(mod, hostile)) {
                            _closeAnnoyingEntities.remove(hostile);
                            continue;
                        }
                        if (!EntityHelper.isActivelyTargetingPlayer(mod, hostile)) {
                            _closeAnnoyingEntities.remove(hostile);
                            continue;
                        }
                        int annoyingRange = hostile instanceof Shulker ? 28 :
                                (hostile instanceof Skeleton || hostile instanceof Witch || hostile
                                        instanceof Pillager || hostile instanceof Piglin || hostile instanceof Stray) ? 15 : 8;
                        boolean isClose = hostile.closerThan(mod.getPlayer(), annoyingRange);

                        if (isClose && !(hostile instanceof Shulker)) {
                            isClose = LookHelper.seesPlayer(hostile, mod.getPlayer(), annoyingRange);
                        }

                        // Give each hostile a timer, if they're close for too long deal with them.
                        if (isClose) {
                            if (!_closeAnnoyingEntities.containsKey(hostile)) {
                                boolean wardenAttacking = hostile instanceof Warden;
                                boolean witherAttacking = hostile instanceof WitherBoss;
                                boolean endermanAttacking = hostile instanceof EnderMan;
                                boolean blazeAttacking = hostile instanceof Blaze;
                                boolean witherSkeletonAttacking = hostile instanceof WitherSkeleton;
                                boolean hoglinAttacking = hostile instanceof Hoglin;
                                boolean zoglinAttacking = hostile instanceof Zoglin;
                                boolean piglinBruteAttacking = hostile instanceof PiglinBrute;
                                if (blazeAttacking || witherSkeletonAttacking || hoglinAttacking || zoglinAttacking ||
                                        piglinBruteAttacking || endermanAttacking || witherAttacking || wardenAttacking) {
                                    if (mod.getPlayer().getHealth() <= 10) {
                                        _closeAnnoyingEntities.put(hostile, new TimerGame(0));
                                    } else {
                                        _closeAnnoyingEntities.put(hostile, new TimerGame(Float.POSITIVE_INFINITY));
                                    }
                                } else {
                                    _closeAnnoyingEntities.put(hostile, new TimerGame(0));
                                }
                                _closeAnnoyingEntities.get(hostile).reset();
                            }
                            if (_closeAnnoyingEntities.get(hostile).elapsed()) {
                                toDealWith.add(hostile);
                            }
                        } else {
                            _closeAnnoyingEntities.remove(hostile);
                        }
                    }
                }
            }

            // Clear dead/non existing hostiles
            List<Entity> toRemove = new ArrayList<>();
            if (!_closeAnnoyingEntities.keySet().isEmpty()) {
                for (Entity check : _closeAnnoyingEntities.keySet()) {
                    if (!check.isAlive()) {
                        toRemove.add(check);
                    }
                }
            }
            if (!toRemove.isEmpty()) {
                for (Entity remove : toRemove) _closeAnnoyingEntities.remove(remove);
            }
            int numberOfProblematicEntities = toDealWith.size();
            if (!toDealWith.isEmpty()) {
                for (Entity ToDealWith : toDealWith) {
                    if (ToDealWith.getClass() == Slime.class || ToDealWith.getClass() == MagmaCube.class) {
                        numberOfProblematicEntities = 1;
                        break;
                    }
                }
            }
            if (numberOfProblematicEntities > 0) {
                int armor = mod.getPlayer().getArmorValue();
                float damage = bestSword == null ? 0 : (1 + swordAttackDamage(bestSword));
                boolean hasShield = mod.getItemStorage().hasItem(Items.SHIELD) ||
                        mod.getItemStorage().hasItemInOffhand(Items.SHIELD);
                int shield = hasShield ? 20 : 0;
                int canDealWith = (int) Math.ceil((armor * 3.6 / 20.0) + (damage * 0.8) + (shield));
                canDealWith += 1;
                if (canDealWith > numberOfProblematicEntities) {
                    // We can deal with it.
                    _runAwayTask = null;
                    for (Entity ToDealWith : toDealWith) {
                        setTask(new KillEntitiesTask(entity -> EntityHelper.isActivelyTargetingPlayer(mod, entity), ToDealWith.getClass()));
                        return 65;
                    }
                    return 65;
                } else {
                    // We can't deal with it
                    _runAwayTask = new RunAwayFromHostilesTask(DANGER_KEEP_DISTANCE, true);
                    setTask(_runAwayTask);
                    return 80;
                }
            }
        }
        // By default if we aren't "immediately" in danger but were running away, keep running away until we're good.
        if (_runAwayTask != null && !_runAwayTask.isFinished(mod)) {
            setTask(_runAwayTask);
            return _cachedLastPriority;
        } else {
            _runAwayTask = null;
        }
        return 0;
    }

    // Material attack-damage bonus (added to the base 1) for each sword tier.
    private static float swordAttackDamage(Item sword) {
        if (sword == Items.NETHERITE_SWORD) return 4;
        if (sword == Items.DIAMOND_SWORD) return 3;
        if (sword == Items.IRON_SWORD) return 2;
        if (sword == Items.STONE_SWORD) return 1;
        return 0; // wooden / golden
    }

    private BlockPos isInsideFireAndOnFire(AltoClef mod) {
        boolean onFire = mod.getPlayer().isOnFire();
        if (!onFire) return null;
        BlockPos p = mod.getPlayer().blockPosition();
        BlockPos[] toCheck = new BlockPos[]{
                p,
                p.offset(1, 0, 0),
                p.offset(1, 0, -1),
                p.offset(0, 0, -1),
                p.offset(-1, 0, -1),
                p.offset(-1, 0, 0),
                p.offset(-1, 0, 1),
                p.offset(0, 0, 1),
                p.offset(1, 0, 1)
        };
        for (BlockPos check : toCheck) {
            Block b = mod.getWorld().getBlockState(check).getBlock();
            if (b instanceof BaseFireBlock) {
                return check;
            }
        }
        return null;
    }

    private void putOutFire(AltoClef mod, BlockPos pos) {
        LookHelper.lookAt(mod, pos);
        if (LookHelper.isLookingAt(mod, pos)) {
            mod.getClientBaritone().getPathingBehavior().cancelEverything();
            mod.getInputControls().hold(Input.CLICK_LEFT);
        }
    }

    private void doEmergencyForceField(AltoClef mod) {
        List<Entity> entities = mod.getEntityTracker().getCloseEntities();
        try {
            for (Entity entity : entities) {
                if (KillAura.isDeflectableProjectile(entity)) {
                    mod.getControllerExtras().attack(entity);
                } else if (entity instanceof Shulker && entity.isAlive()
                        && !mod.getBehaviour().shouldExcludeFromForcefield(entity)
                        && mod.getControllerExtras().inRange(entity)) {
                    // Kill shulkers on sight during user tasks — no shielding, no pathing interrupt
                    KillAura.equipWeapon(mod);
                    LookHelper.lookAt(mod, entity.getEyePosition());
                    if (mod.getPlayer().getAttackStrengthScale(0) >= 1) {
                        mod.getControllerExtras().attack(entity);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        _killAura.stopShielding(mod);
        stopShielding(mod);
    }

    private boolean hitCloseShulkerBullet(AltoClef mod) {
        Optional<Entity> bullet = mod.getEntityTracker().getClosestEntity(
                entity -> mod.getControllerExtras().inRange(entity),
                ShulkerBullet.class);
        if (bullet.isEmpty()) return false;
        // No cancelEverything() — deflect the bullet without freezing movement
        _killAura.stopShielding(mod);
        stopShielding(mod);
        LookHelper.lookAt(mod, bullet.get().position());
        mod.getControllerExtras().attack(bullet.get());
        return true;
    }

    private float getEmergencyPriorityForUserTask(AltoClef mod) {
        _doingFunkyStuff = false;

        Creeper blowingUp = getClosestFusingCreeper(mod);
        if (blowingUp != null && getCreeperSafety(mod.getPlayer().position(), blowingUp) < 16) {
            _doingFunkyStuff = true;
            _runAwayTask = new RunAwayFromCreepersTask(CREEPER_KEEP_DISTANCE);
            setTask(_runAwayTask);
            return 95;
        }

        if (mod.getPlayer().getHealth() <= 8 && mod.getModSettings().isDodgeProjectiles() && isProjectileClose(mod)) {
            _doingFunkyStuff = true;
            _runAwayTask = new DodgeProjectilesTask(ARROW_KEEP_DISTANCE_HORIZONTAL, ARROW_KEEP_DISTANCE_VERTICAL);
            setTask(_runAwayTask);
            return 95;
        }

        Optional<Entity> universallyDangerous = getUniversallyDangerousMob(mod);
        if (universallyDangerous.isPresent() || (mod.getPlayer().getHealth() <= 8 && isInDanger(mod))) {
            _runAwayTask = new RunAwayFromHostilesTask(DANGER_KEEP_DISTANCE, true);
            setTask(_runAwayTask);
            return 95;
        }

        _runAwayTask = null;
        _closeAnnoyingEntities.clear();
        setTask(null);
        return Float.NEGATIVE_INFINITY;
    }

    private void doForceField(AltoClef mod) {
        _killAura.tickStart();

        // Hit all hostiles close to us.
        List<Entity> entities = mod.getEntityTracker().getCloseEntities();
        try {
            if (!entities.isEmpty()) {
                for (Entity entity : entities) {
                    boolean shouldForce = false;
                    if (mod.getBehaviour().shouldExcludeFromForcefield(entity) || shouldIgnoreBlazeForSkulls(mod, entity)) continue;
                    if (entity instanceof Mob) {
                        if (entity instanceof Shulker) {
                            shouldForce = true;
                        } else if (EntityHelper.isGenerallyHostileToPlayer(mod, entity)) {
                            if (LookHelper.seesPlayer(entity, mod.getPlayer(), 10)) {
                                shouldForce = true;
                            }
                        }
                    } else if (KillAura.isDeflectableProjectile(entity)) {
                        shouldForce = true;
                    } else if (entity instanceof Player player && mod.getBehaviour().shouldForceFieldPlayers()) {
                        if (!player.equals(mod.getPlayer())) {
                            shouldForce = true;
                        }
                    }
                    if (shouldForce) {
                        applyForceField(entity);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        _killAura.tickEnd(mod);
    }

    private void applyForceField(Entity entity) {
        _killAura.applyAura(entity);
    }

    private Creeper getClosestFusingCreeper(AltoClef mod) {
        double worstSafety = Float.POSITIVE_INFINITY;
        Creeper target = null;
        try {
            List<Creeper> creepers = mod.getEntityTracker().getTrackedEntities(Creeper.class);
            if (!creepers.isEmpty()) {
                for (Creeper creeper : creepers) {
                    if (creeper == null) continue;
                    if (creeper.getSwelling(1.0f) < 0.001) continue;

                    double safety = getCreeperSafety(mod.getPlayer().position(), creeper);
                    if (safety < worstSafety) {
                        target = creeper;
                    }
                }
            }
        } catch (ConcurrentModificationException | ArrayIndexOutOfBoundsException | NullPointerException e) {
            Debug.logWarning("Weird Exception caught and ignored while scanning for creepers: " + e.getMessage());
            return target;
        }
        return target;
    }

    private boolean isProjectileClose(AltoClef mod) {
        List<CachedProjectile> projectiles = mod.getEntityTracker().getProjectiles();
        try {
            if (!projectiles.isEmpty()) {
                for (CachedProjectile projectile : projectiles) {
                    if (projectile.position.distanceToSqr(mod.getPlayer().position()) < 150) {
                        boolean isGhastBall = projectile.projectileType == LargeFireball.class;
                        if (isGhastBall) {
                            Optional<Entity> ghastBall = mod.getEntityTracker().getClosestEntity(LargeFireball.class);
                            Optional<Entity> ghast = mod.getEntityTracker().getClosestEntity(Ghast.class);
                            if (ghastBall.isPresent() && ghast.isPresent() && _runAwayTask == null) {
                                mod.getClientBaritone().getPathingBehavior().cancelEverything();
                                LookHelper.lookAt(mod, ghast.get().getEyePosition());
                            }
                            return false;
                            // Ignore ghast balls
                        }
                        if (projectile.projectileType == DragonFireball.class) {
                            // Ignore dragon fireballs
                            return false;
                        }
                        if (projectile.projectileType == ShulkerBullet.class) {
                            // Ignore shulker bullets — bot has high armor, dodging them causes analysis paralysis
                            return false;
                        }

                        Vec3 expectedHit = ProjectileHelper.calculateArrowClosestApproach(projectile, mod.getPlayer());

                        Vec3 delta = mod.getPlayer().position().subtract(expectedHit);

                        double horizontalDistanceSq = delta.x * delta.x + delta.z * delta.z;
                        double verticalDistance = Math.abs(delta.y);
                        if (horizontalDistanceSq < ARROW_KEEP_DISTANCE_HORIZONTAL * ARROW_KEEP_DISTANCE_HORIZONTAL && verticalDistance < ARROW_KEEP_DISTANCE_VERTICAL) {
                            if (_runAwayTask == null) {
                                mod.getClientBaritone().getPathingBehavior().cancelEverything();
                                LookHelper.lookAt(mod, projectile.position);
                            }
                            return true;
                        }
                    }
                }
            }
        } catch (ConcurrentModificationException ignored) {
        }
        return false;
    }

    private Optional<Entity> getUniversallyDangerousMob(AltoClef mod) {
        Optional<Entity> warden = mod.getEntityTracker().getClosestEntity(Warden.class);
        if (warden.isPresent()) {
            double range = SAFE_KEEP_DISTANCE - 2;
            if (!isCurrentTarget(mod, warden.get()) && warden.get().distanceToSqr(mod.getPlayer()) < range * range && EntityHelper.isAngryAtPlayer(mod, warden.get())) {
                return warden;
            }
        }
        Optional<Entity> wither = mod.getEntityTracker().getClosestEntity(WitherBoss.class);
        if (wither.isPresent()) {
            double range = SAFE_KEEP_DISTANCE - 2;
            if (!isCurrentTarget(mod, wither.get()) && wither.get().distanceToSqr(mod.getPlayer()) < range * range && EntityHelper.isAngryAtPlayer(mod, wither.get())) {
                return wither;
            }
        }
        Optional<Entity> witherSkeleton = mod.getEntityTracker().getClosestEntity(WitherSkeleton.class);
        if (witherSkeleton.isPresent()) {
            double range = SAFE_KEEP_DISTANCE - 2;
            if (!isCurrentTarget(mod, witherSkeleton.get()) && witherSkeleton.get().distanceToSqr(mod.getPlayer()) < range * range && EntityHelper.isAngryAtPlayer(mod, witherSkeleton.get())) {
                return witherSkeleton;
            }
        }
        Optional<Entity> hoglin = mod.getEntityTracker().getClosestEntity(Hoglin.class);
        if (hoglin.isPresent()) {
            double range = SAFE_KEEP_DISTANCE - 2;
            if (!isCurrentTarget(mod, hoglin.get()) && hoglin.get().distanceToSqr(mod.getPlayer()) < range * range && EntityHelper.isAngryAtPlayer(mod, hoglin.get())) {
                return hoglin;
            }
        }
        Optional<Entity> zoglin = mod.getEntityTracker().getClosestEntity(Zoglin.class);
        if (zoglin.isPresent()) {
            double range = SAFE_KEEP_DISTANCE - 2;
            if (!isCurrentTarget(mod, zoglin.get()) && zoglin.get().distanceToSqr(mod.getPlayer()) < range * range && EntityHelper.isAngryAtPlayer(mod, zoglin.get())) {
                return zoglin;
            }
        }
        Optional<Entity> piglinBrute = mod.getEntityTracker().getClosestEntity(PiglinBrute.class);
        if (piglinBrute.isPresent()) {
            double range = SAFE_KEEP_DISTANCE - 2;
            if (!isCurrentTarget(mod, piglinBrute.get()) && piglinBrute.get().distanceToSqr(mod.getPlayer()) < range * range && EntityHelper.isAngryAtPlayer(mod, piglinBrute.get())) {
                return piglinBrute;
            }
        }
        return Optional.empty();
    }

    private boolean isInDanger(AltoClef mod) {
        Optional<Entity> witch = mod.getEntityTracker().getClosestEntity(Witch.class);
        boolean hasFood = mod.getFoodChain().hasFood();
        float health = mod.getPlayer().getHealth();
        if (health <= 10 && hasFood && witch.isEmpty()) {
            return true;
        }
        if (mod.getPlayer().hasEffect(MobEffects.WITHER) ||
                (mod.getPlayer().hasEffect(MobEffects.POISON) && witch.isEmpty())) {
            return true;
        }
        if (isVulnurable(mod)) {
            // If hostile mobs are nearby...
            try {
                Player player = mod.getPlayer();
                List<Entity> hostiles = mod.getEntityTracker().getHostiles();
                if (!hostiles.isEmpty()) {
                    synchronized (BaritoneHelper.MINECRAFT_LOCK) {
                        for (Entity entity : hostiles) {
                            if (entity.closerThan(player, SAFE_KEEP_DISTANCE) &&
                                    !mod.getBehaviour().shouldExcludeFromForcefield(entity) &&
                                    !shouldIgnoreBlazeForSkulls(mod, entity) &&
                                    EntityHelper.isAngryAtPlayer(mod, entity)) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Debug.logWarning("Weird multithread exception. Will fix later.");
            }
        }
        return false;
    }

    private boolean isVulnurable(AltoClef mod) {
        int armor = mod.getPlayer().getArmorValue();
        float health = mod.getPlayer().getHealth();
        if (armor <= 15 && health < 3) return true;
        if (armor < 10 && health < 10) return true;
        return armor < 5 && health < 18;
    }

    private boolean isCurrentTarget(AltoClef mod, Entity entity) {
        if (entity.equals(_targetEntity)) return true;
        return entity instanceof WitherSkeleton && isWitherSkullTaskActive(mod);
    }

    private boolean shouldIgnoreBlazeForSkulls(AltoClef mod, Entity entity) {
        return entity instanceof Blaze && isWitherSkullTaskActive(mod);
    }

    private boolean isWitherSkullTaskActive(AltoClef mod) {
        Task task = mod.getUserTaskChain().getCurrentTask();
        return task != null && task.thisOrChildSatisfies(child ->
                child instanceof KillAndLootTask killTask && killTask.isWitherSkullTask());
    }

    public void setTargetEntity(Entity entity) {
        _targetEntity = entity;
    }

    public void resetTargetEntity() {
        _targetEntity = null;
    }

    public void setForceFieldRange(double range) {
        _killAura.setRange(range);
    }

    public void resetForceField() {
        _killAura.setRange(Double.POSITIVE_INFINITY);
    }

    public boolean isDoingAcrobatics() {
        return _doingFunkyStuff;
    }

    public boolean isPuttingOutFire() {
        return _wasPuttingOutFire;
    }

    @Override
    public boolean isActive() {
        // We're always checking for mobs
        return true;
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {
        // Task is done, so I guess we move on?
    }

    @Override
    public String getName() {
        return "Mob Defense";
    }
}
