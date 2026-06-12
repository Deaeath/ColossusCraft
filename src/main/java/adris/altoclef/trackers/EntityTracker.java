package adris.altoclef.trackers;

import adris.altoclef.Debug;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.PlayerCollidedWithEntityEvent;
import adris.altoclef.mixins.PersistentProjectileEntityAccessor;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.baritone.CachedProjectile;
import adris.altoclef.util.helpers.ProjectileHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@SuppressWarnings({"rawtypes", "unchecked"})
public class EntityTracker extends Tracker {

    private final Map<Item, List<ItemEntity>> itemDropLocations = new HashMap<>();
    private final Map<Class, List<Entity>> entityMap = new HashMap<>();
    private final List<Entity> closeEntities = new ArrayList<>();
    private final List<Entity> hostiles = new ArrayList<>();
    private final List<CachedProjectile> projectiles = new ArrayList<>();
    private final Map<String, Player> playerMap = new HashMap<>();
    private final Map<String, Vec3> playerLastCoordinates = new HashMap<>();
    private final Set<UUID> unreachableEntities = new HashSet<>();
    private final Map<Player, List<Entity>> collisionAccumulator = new HashMap<>();
    private final Map<Player, Set<Entity>> collisions = new HashMap<>();

    public EntityTracker(TrackerManager manager) {
        super(manager);
        EventBus.subscribe(PlayerCollidedWithEntityEvent.class, evt -> registerPlayerCollision(evt.player, evt.other));
    }

    private static Class squashType(Class type) {
        if (Player.class.isAssignableFrom(type)) return Player.class;
        return type;
    }

    private void registerPlayerCollision(Player player, Entity entity) {
        collisionAccumulator.computeIfAbsent(player, ignored -> new ArrayList<>()).add(entity);
    }

    public boolean isCollidingWithPlayer(Player player, Entity entity) {
        return collisions.containsKey(player) && collisions.get(player).contains(entity);
    }

    public boolean isCollidingWithPlayer(Entity entity) {
        return mod.getPlayer() != null && isCollidingWithPlayer(mod.getPlayer(), entity);
    }

    public Optional<ItemEntity> getClosestItemDrop(Item... items) {
        return mod.getPlayer() == null ? Optional.empty() : getClosestItemDrop(mod.getPlayer().position(), items);
    }

    public Optional<ItemEntity> getClosestItemDrop(ItemTarget... items) {
        return mod.getPlayer() == null ? Optional.empty() : getClosestItemDrop(mod.getPlayer().position(), items);
    }

    public Optional<ItemEntity> getClosestItemDrop(Vec3 position, Item... items) {
        return getClosestItemDrop(position, entity -> true, items);
    }

    public Optional<ItemEntity> getClosestItemDrop(Vec3 position, ItemTarget... items) {
        return getClosestItemDrop(position, entity -> true, items);
    }

    public Optional<ItemEntity> getClosestItemDrop(Predicate<ItemEntity> acceptPredicate, Item... items) {
        return mod.getPlayer() == null ? Optional.empty() : getClosestItemDrop(mod.getPlayer().position(), acceptPredicate, items);
    }

    public Optional<ItemEntity> getClosestItemDrop(Vec3 position, Predicate<ItemEntity> acceptPredicate, Item... items) {
        ItemTarget[] targets = new ItemTarget[items.length];
        for (int i = 0; i < items.length; ++i) {
            targets[i] = new ItemTarget(items[i], 9999999);
        }
        return getClosestItemDrop(position, acceptPredicate, targets);
    }

    public Optional<ItemEntity> getClosestItemDrop(Vec3 position, Predicate<ItemEntity> acceptPredicate, ItemTarget... targets) {
        ensureUpdated();
        if (targets.length == 0) {
            Debug.logError("Asked for zero item drops");
            return Optional.empty();
        }
        ItemEntity closest = null;
        double minCost = Double.POSITIVE_INFINITY;
        for (ItemTarget target : targets) {
            for (Item item : target.getMatches()) {
                for (ItemEntity entity : itemDropLocations.getOrDefault(item, Collections.emptyList())) {
                    if (!isEntityReachable(entity) || !entity.isAlive() || !acceptPredicate.test(entity)) continue;
                    double cost = entity.position().distanceToSqr(position);
                    if (cost < minCost) {
                        minCost = cost;
                        closest = entity;
                    }
                }
            }
        }
        return Optional.ofNullable(closest);
    }

    public Optional<Entity> getClosestEntity(Class... entityTypes) {
        return mod.getPlayer() == null ? Optional.empty() : getClosestEntity(mod.getPlayer().position(), entityTypes);
    }

    public Optional<Entity> getClosestEntity(Vec3 position, Class... entityTypes) {
        return getClosestEntity(position, entity -> true, entityTypes);
    }

    public Optional<Entity> getClosestEntity(Predicate<Entity> acceptPredicate, Class... entityTypes) {
        return mod.getPlayer() == null ? Optional.empty() : getClosestEntity(mod.getPlayer().position(), acceptPredicate, entityTypes);
    }

    public Optional<Entity> getClosestEntity(Vec3 position, Predicate<Entity> acceptPredicate, Class... entityTypes) {
        ensureUpdated();
        Entity closest = null;
        double minCost = Double.POSITIVE_INFINITY;
        for (Class type : entityTypes) {
            for (Entity entity : matchingEntities(type)) {
                if (!isEntityReachable(entity) || !entity.isAlive() || !acceptPredicate.test(entity)) continue;
                double cost = entity.position().distanceToSqr(position);
                if (cost < minCost) {
                    minCost = cost;
                    closest = entity;
                }
            }
        }
        return Optional.ofNullable(closest);
    }

    public boolean itemDropped(Item... items) {
        ensureUpdated();
        for (Item item : items) {
            for (ItemEntity entity : itemDropLocations.getOrDefault(item, Collections.emptyList())) {
                if (isEntityReachable(entity)) return true;
            }
        }
        return false;
    }

    public boolean itemDropped(ItemTarget... targets) {
        ensureUpdated();
        for (ItemTarget target : targets) {
            if (itemDropped(target.getMatches())) return true;
        }
        return false;
    }

    public List<ItemEntity> getDroppedItems() {
        ensureUpdated();
        List<ItemEntity> result = new ArrayList<>();
        itemDropLocations.values().forEach(result::addAll);
        return result;
    }

    public boolean entityFound(Predicate<Entity> shouldAccept, Class... types) {
        ensureUpdated();
        for (Class type : types) {
            for (Entity entity : matchingEntities(type)) {
                if (shouldAccept.test(entity)) return true;
            }
        }
        return false;
    }

    public boolean entityFound(Class... types) {
        return entityFound(entity -> true, types);
    }

    public <T extends Entity> List<T> getTrackedEntities(Class<T> type) {
        ensureUpdated();
        return (List<T>) matchingEntities(type);
    }

    public List<Entity> getCloseEntities() {
        ensureUpdated();
        return closeEntities;
    }

    public List<CachedProjectile> getProjectiles() {
        ensureUpdated();
        return projectiles;
    }

    public List<Entity> getHostiles() {
        ensureUpdated();
        return hostiles;
    }

    public boolean isPlayerLoaded(String name) {
        ensureUpdated();
        return playerMap.containsKey(name);
    }

    public Optional<Vec3> getPlayerMostRecentPosition(String name) {
        ensureUpdated();
        return Optional.ofNullable(playerLastCoordinates.get(name));
    }

    public Optional<Player> getPlayerEntity(String name) {
        ensureUpdated();
        return Optional.ofNullable(playerMap.get(name));
    }

    public void requestEntityUnreachable(Entity entity) {
        unreachableEntities.add(entity.getUUID());
    }

    public boolean isEntityReachable(Entity entity) {
        return !unreachableEntities.contains(entity.getUUID());
    }

    private List<Entity> matchingEntities(Class type) {
        List<Entity> result = new ArrayList<>();
        for (Map.Entry<Class, List<Entity>> entry : entityMap.entrySet()) {
            if (type.isAssignableFrom(entry.getKey())) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    @Override
    protected synchronized void updateState() {
        itemDropLocations.clear();
        entityMap.clear();
        closeEntities.clear();
        projectiles.clear();
        hostiles.clear();
        playerMap.clear();
        collisions.clear();
        collisionAccumulator.forEach((player, entities) -> collisions.put(player, new HashSet<>(entities)));
        collisionAccumulator.clear();
        if (Minecraft.getInstance().level == null) return;
        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (entity == null || !entity.isAlive() || entity.equals(mod.getPlayer())) continue;
            Class type = squashType(entity.getClass());
            entityMap.computeIfAbsent(type, ignored -> new ArrayList<>()).add(entity);
            if (mod.getControllerExtras().inRange(entity)) {
                closeEntities.add(entity);
            }
            if (entity instanceof ItemEntity itemEntity) {
                itemDropLocations.computeIfAbsent(itemEntity.getItem().getItem(), ignored -> new ArrayList<>()).add(itemEntity);
            }
            if (entity instanceof Enemy && mod.getPlayer() != null && entity.distanceTo(mod.getPlayer()) < 26) {
                hostiles.add(entity);
            }
            if (entity instanceof Projectile projectile && shouldTrackProjectile(projectile)) {
                CachedProjectile cached = new CachedProjectile();
                cached.position = projectile.position();
                cached.velocity = projectile.getDeltaMovement();
                cached.gravity = ProjectileHelper.hasGravity(projectile) ? ProjectileHelper.ARROW_GRAVITY_ACCEL : 0;
                cached.projectileType = projectile.getClass();
                projectiles.add(cached);
            }
            if (entity instanceof Player player) {
                String name = player.getName().getString();
                playerMap.put(name, player);
                playerLastCoordinates.put(name, player.position());
            }
        }
    }

    private boolean shouldTrackProjectile(Projectile projectile) {
        if (projectile instanceof FishingHook || projectile instanceof ThrownEnderpearl || projectile instanceof ThrownExperienceBottle) {
            return false;
        }
        if (projectile.getOwner() == mod.getPlayer()) {
            return false;
        }
        return !(projectile instanceof AbstractArrow arrow) || !((PersistentProjectileEntityAccessor) arrow).isInGround();
    }

    @Override
    protected void reset() {
        unreachableEntities.clear();
        itemDropLocations.clear();
        entityMap.clear();
        closeEntities.clear();
        projectiles.clear();
        hostiles.clear();
        playerMap.clear();
        playerLastCoordinates.clear();
        collisions.clear();
        collisionAccumulator.clear();
    }
}
