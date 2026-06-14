package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.Settings;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.tasks.speedrun.DragonBreathTracker;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.PlayerSlot;
import baritone.api.utils.input.Input;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;
import java.util.Optional;

/**
 * Auto-eat + proactive food collection, ported verbatim from upstream AltoClef (1.19.4 -> 1.21.1).
 * Eating runs as a background side-effect in getPriority() (returns NEGATIVE_INFINITY, never interrupts
 * the user task). When our total food score drops below settings, the chain becomes ACTIVE at priority
 * 55 and runs a CollectFoodTask to go gather/hunt food. Config values inlined (no config file).
 */
public class FoodChain extends SingleTaskChain {
    // --- inlined FoodChainConfig defaults ---
    private static final int alwaysEatWhenWitherOrFireAndHealthBelow = 6;
    private static final int alwaysEatWhenBelowHunger = 10;
    private static final int alwaysEatWhenBelowHealth = 14;
    private static final int alwaysEatWhenBelowHungerAndPerfectFit = 20 - 5;
    private static final int prioritizeSaturationWhenBelowHealth = 8;
    private static final float foodPickPrioritizeSaturationSaturationMultiplier = 8;
    private static final float foodPickSaturationWastePenaltyMultiplier = 1;
    private static final float foodPickHungerWastePenaltyMultiplier = 2;
    private static final float foodPickHungerNotFilledPenaltyMultiplier = 1;
    private static final float foodPickRottenFleshPenalty = 100;

    private final DragonBreathTracker _dragonBreathTracker = new DragonBreathTracker();
    boolean _hasFood;
    private boolean _isTryingToEat = false;
    private boolean _requestFillup = false;
    private boolean _needsFood = false;
    private Optional<Item> _cachedPerfectFood = Optional.empty();
    private boolean shouldStop = false;

    public FoodChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {
        mainTask = null;
    }

    private void startEat(AltoClef mod, Item food) {
        _isTryingToEat = true;
        _requestFillup = true;
        mod.getSlotHandler().forceEquipItem(new Item[]{food}, true); // "true" because it's food
        mod.getInputControls().hold(Input.CLICK_RIGHT);
        mod.getExtraBaritoneSettings().setInteractionPaused(true);
    }

    private void stopEat(AltoClef mod) {
        if (_isTryingToEat) {
            if (mod.getItemStorage().hasItem(Items.SHIELD) || mod.getItemStorage().hasItemInOffhand(Items.SHIELD)) {
                if (StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT).getItem() != Items.SHIELD) {
                    mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
                } else {
                    _isTryingToEat = false;
                    _requestFillup = false;
                }
            } else {
                _isTryingToEat = false;
                _requestFillup = false;
            }
            mod.getInputControls().release(Input.CLICK_RIGHT);
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
        }
    }

    public boolean isTryingToEat() {
        return _isTryingToEat;
    }

    @Override
    public float getPriority(AltoClef mod) {
        if (WorldHelper.isInNetherPortal(mod)) {
            stopEat(mod);
            return Float.NEGATIVE_INFINITY;
        }
        if (mod.getMobDefenseChain().isPuttingOutFire()) {
            stopEat(mod);
            return Float.NEGATIVE_INFINITY;
        }
        _dragonBreathTracker.updateBreath(mod);
        for (BlockPos playerIn : WorldHelper.getBlocksTouchingPlayer(mod)) {
            if (_dragonBreathTracker.isTouchingDragonBreath(playerIn)) {
                stopEat(mod);
                return Float.NEGATIVE_INFINITY;
            }
        }

        if (!AltoClef.inGame()) {
            stopEat(mod);
            return Float.NEGATIVE_INFINITY;
        }

        if (!mod.getModSettings().isAutoEat()) {
            stopEat(mod);
            return Float.NEGATIVE_INFINITY;
        }

        // do NOT eat while in lava if we are escaping it
        if (mod.getPlayer().isInLava()) {
            stopEat(mod);
            return Float.NEGATIVE_INFINITY;
        }

        // We're in danger, don't eat now!!
        if (!mod.getMLGBucketChain().doneMLG(mod) || mod.getMLGBucketChain().isFallingOhNo(mod) ||
                mod.getPlayer().isBlocking() || shouldStop) {
            stopEat(mod);
            return Float.NEGATIVE_INFINITY;
        }
        Pair<Integer, Optional<Item>> calculation = calculateFood(mod);
        int _cachedFoodScore = calculation.getFirst();
        _cachedPerfectFood = calculation.getSecond();

        boolean hasFood = _cachedFoodScore > 0;
        _hasFood = hasFood;

        // If we requested a fillup but we're full, stop.
        if (_requestFillup && mod.getPlayer().getFoodData().getFoodLevel() >= 20) {
            _requestFillup = false;
        }
        // If we no longer have food, we no longer can eat.
        if (!hasFood) {
            _requestFillup = false;
        }
        if (hasFood && (needsToEat() || _requestFillup) && _cachedPerfectFood.isPresent() &&
                !mod.getMLGBucketChain().isChorusFruiting() && !mod.getPlayer().isBlocking()) {
            Item toUse = _cachedPerfectFood.get();
            startEat(mod, toUse);
        } else {
            stopEat(mod);
        }

        Settings settings = mod.getModSettings();

        // Autonomous food GATHERING (hunting/harvesting) is opt-in — off by default so the bot never
        // wanders off to kill animals / tear up the base on its own. Auto-EATING above still runs.
        // Use /food <n> or /get <food> to gather on demand.
        if (settings.isAutoCollectFood() && (_needsFood || _cachedFoodScore < settings.getMinimumFoodAllowed())) {
            _needsFood = _cachedFoodScore < settings.getFoodUnitsToCollect();

            // Only collect if we don't have enough food.
            if (_cachedFoodScore < settings.getFoodUnitsToCollect()) {
                setTask(new CollectFoodTask(settings.getFoodUnitsToCollect()));
                return 55f;
            }
        }

        // Food eating is handled asynchronously.
        return Float.NEGATIVE_INFINITY;
    }

    @Override
    public boolean isActive() {
        // We're always checking for food.
        return true;
    }

    @Override
    public boolean isPassive() {
        return true;
    }

    @Override
    public boolean pausesBaritone() {
        return mainTask != null;
    }

    @Override
    public String getName() {
        return "Food";
    }

    @Override
    protected void onStop(AltoClef mod) {
        super.onStop(mod);
        stopEat(mod);
    }

    public boolean needsToEat() {
        if (!hasFood() || shouldStop) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        int foodLevel = player.getFoodData().getFoodLevel();
        float health = player.getHealth();

        if (health <= 10 && foodLevel <= 19) {
            return true;
        }
        if (foodLevel >= 20) {
            // We can't eat.
            return false;
        } else {
            // Eat if we're desperate/need to heal ASAP
            if (player.isOnFire() || player.hasEffect(MobEffects.WITHER) || health < alwaysEatWhenWitherOrFireAndHealthBelow) {
                return true;
            } else if (foodLevel > alwaysEatWhenBelowHunger) {
                if (health < alwaysEatWhenBelowHealth) {
                    return true;
                }
            } else {
                // We have half hunger
                return true;
            }
        }

        // Eat if we're units hungry and we have a perfect fit.
        if (foodLevel < alwaysEatWhenBelowHungerAndPerfectFit && _cachedPerfectFood.isPresent()) {
            int need = 20 - foodLevel;
            Item best = _cachedPerfectFood.get();
            FoodProperties props = best.components().get(DataComponents.FOOD);
            int fills = (props != null) ? props.nutrition() : -1;
            return fills == need;
        }

        return false;
    }

    private Pair<Integer, Optional<Item>> calculateFood(AltoClef mod) {
        Item bestFood = null;
        double bestFoodScore = Double.NEGATIVE_INFINITY;
        int foodTotal = 0;
        LocalPlayer player = mod.getPlayer();
        float health = player != null ? player.getHealth() : 20;
        float hunger = player != null ? player.getFoodData().getFoodLevel() : 20;
        float saturation = player != null ? player.getFoodData().getSaturationLevel() : 20;
        // Get best food item + calculate food total
        for (ItemStack stack : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null) {
                // Ignore protected items
                if (!ItemHelper.canThrowAwayStack(mod, stack)) continue;

                // Ignore spider eyes
                if (stack.getItem() == Items.SPIDER_EYE) {
                    continue;
                }

                float hungerIfEaten = Math.min(hunger + food.nutrition(), 20);
                float saturationIfEaten = Math.min(hungerIfEaten, saturation + food.saturation());
                float gainedSaturation = (saturationIfEaten - saturation);
                float gainedHunger = (hungerIfEaten - hunger);
                float hungerNotFilled = 20 - hungerIfEaten;

                float saturationWasted = food.saturation() - gainedSaturation;
                float hungerWasted = food.nutrition() - gainedHunger;

                boolean prioritizeSaturation = health < prioritizeSaturationWhenBelowHealth;
                float saturationGoodScore = prioritizeSaturation ? gainedSaturation * foodPickPrioritizeSaturationSaturationMultiplier : gainedSaturation;
                float saturationLossPenalty = prioritizeSaturation ? 0 : saturationWasted * foodPickSaturationWastePenaltyMultiplier;
                float hungerLossPenalty = hungerWasted * foodPickHungerWastePenaltyMultiplier;
                float hungerNotFilledPenalty = hungerNotFilled * foodPickHungerNotFilledPenaltyMultiplier;

                float score = saturationGoodScore - saturationLossPenalty - hungerLossPenalty - hungerNotFilledPenalty;

                if (stack.getItem() == Items.ROTTEN_FLESH) {
                    score -= foodPickRottenFleshPenalty;
                }
                if (score > bestFoodScore) {
                    bestFoodScore = score;
                    bestFood = stack.getItem();
                }

                foodTotal += Objects.requireNonNull(stack.get(DataComponents.FOOD)).nutrition() * stack.getCount();
            }
        }

        return Pair.of(foodTotal, Optional.ofNullable(bestFood));
    }

    public boolean needsToEatCritical() {
        return false;
    }

    public boolean hasFood() {
        return _hasFood;
    }

    public void shouldStop(boolean shouldStopInput) {
        shouldStop = shouldStopInput;
    }

    public boolean isShouldStop() {
        return shouldStop;
    }
}
