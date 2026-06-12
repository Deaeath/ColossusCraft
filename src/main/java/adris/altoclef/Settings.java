package adris.altoclef;

import adris.altoclef.control.KillAura;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.List;

public class Settings {
    private String commandPrefix = "@";
    private String chatLogPrefix = "[ColossusCraft] ";
    private boolean hideAllWarningLogs;
    private double entityReachRange = 4.5;
    private double containerItemMoveDelay = 0.12;
    private int blockScanHorizontalRange = 48;
    private int blockScanVerticalRange = 32;
    private boolean mobDefense = true;
    private boolean autoEat = true;
    private boolean dodgeProjectiles = true;
    private boolean dealWithAnnoyingHostiles = true;
    private boolean autoMLGBucket = true;
    private boolean extinguishSelfWithWater = true;
    private boolean avoidDrowning = true;
    private boolean closeScreenWhenLookingOrMining = true;
    private int foodUnitsToCollect = 40;
    private int minimumFoodAllowed = 10;
    private boolean useBlastFurnace = true;
    private boolean replantCrops = true;
    private KillAura.Strategy forceFieldStrategy = KillAura.Strategy.SMART;

    public String getCommandPrefix() {
        return commandPrefix;
    }

    public String getChatLogPrefix() {
        return chatLogPrefix;
    }

    public boolean shouldHideAllWarningLogs() {
        return hideAllWarningLogs;
    }

    public double getEntityReachRange() {
        return entityReachRange;
    }

    public double getContainerItemMoveDelay() {
        return containerItemMoveDelay;
    }

    public int getBlockScanHorizontalRange() {
        return blockScanHorizontalRange;
    }

    public int getBlockScanVerticalRange() {
        return blockScanVerticalRange;
    }

    public boolean isMobDefense() {
        return mobDefense;
    }

    public void setMobDefense(boolean mobDefense) {
        this.mobDefense = mobDefense;
    }

    public boolean isAutoEat() {
        return autoEat;
    }

    public void setAutoEat(boolean autoEat) {
        this.autoEat = autoEat;
    }

    // Autonomous food gathering (hunting/harvesting) — OFF by default; eating still happens.
    private boolean autoCollectFood = false;

    public boolean isAutoCollectFood() {
        return autoCollectFood;
    }

    public void setAutoCollectFood(boolean value) {
        autoCollectFood = value;
    }

    public boolean isDodgeProjectiles() {
        return dodgeProjectiles;
    }

    public void setDodgeProjectiles(boolean dodgeProjectiles) {
        this.dodgeProjectiles = dodgeProjectiles;
    }

    public boolean shouldDealWithAnnoyingHostiles() {
        return dealWithAnnoyingHostiles;
    }

    public void setDealWithAnnoyingHostiles(boolean dealWithAnnoyingHostiles) {
        this.dealWithAnnoyingHostiles = dealWithAnnoyingHostiles;
    }

    public boolean shouldUseBlastFurnace() {
        return useBlastFurnace;
    }

    public boolean shouldReplantCrops() {
        return replantCrops;
    }

    private boolean limitFuelsToSupportedFuels = true;
    private final List<Item> supportedFuels = List.of(Items.COAL, Items.CHARCOAL);
    private final List<Item> throwawayItems = Arrays.asList(
            // Overworld junk
            Items.DRIPSTONE_BLOCK, Items.ROOTED_DIRT, Items.GRAVEL, Items.SAND, Items.DIORITE, Items.ANDESITE,
            Items.GRANITE, Items.TUFF, Items.COBBLESTONE, Items.DIRT, Items.COBBLED_DEEPSLATE,
            Items.ACACIA_LEAVES, Items.BIRCH_LEAVES, Items.DARK_OAK_LEAVES, Items.OAK_LEAVES, Items.JUNGLE_LEAVES, Items.SPRUCE_LEAVES,
            // Nether junk
            Items.NETHERRACK, Items.MAGMA_BLOCK, Items.SOUL_SOIL, Items.SOUL_SAND, Items.NETHER_BRICKS, Items.NETHER_BRICK,
            Items.BASALT, Items.BLACKSTONE, Items.END_STONE, Items.SANDSTONE, Items.STONE_BRICKS
    );

    public Item[] getThrowawayItems(AltoClef mod, boolean includeProtected) {
        return throwawayItems.stream().filter(item -> includeProtected || !mod.getBehaviour().isProtected(item)).toArray(Item[]::new);
    }

    public Item[] getThrowawayItems(AltoClef mod) {
        return getThrowawayItems(mod, false);
    }

    public boolean isSupportedFuel(Item item) {
        return !limitFuelsToSupportedFuels || supportedFuels.contains(item);
    }

    public boolean shouldAutoMLGBucket() {
        return autoMLGBucket;
    }

    public boolean shouldExtinguishSelfWithWater() {
        return extinguishSelfWithWater;
    }

    public boolean shouldAvoidDrowning() {
        return avoidDrowning;
    }

    public boolean shouldCloseScreenWhenLookingOrMining() {
        return closeScreenWhenLookingOrMining;
    }

    public int getFoodUnitsToCollect() {
        return foodUnitsToCollect;
    }

    public int getMinimumFoodAllowed() {
        return minimumFoodAllowed;
    }

    public KillAura.Strategy getForceFieldStrategy() {
        return forceFieldStrategy;
    }

    public boolean shouldThrowawayUnusedItems() {
        return true;
    }
}
