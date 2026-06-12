package adris.altoclef;

import adris.altoclef.chains.UserTaskChain;
import adris.altoclef.chains.MobDefenseChain;
import adris.altoclef.chains.FoodChain;
import adris.altoclef.chains.DeathMenuChain;
import adris.altoclef.chains.MLGBucketFallChain;
import adris.altoclef.chains.WorldSurvivalChain;
import adris.altoclef.chains.PlayerInteractionFixChain;
import adris.altoclef.control.KillAura;
import adris.altoclef.util.baritone.AltoClefSettings;
import adris.altoclef.commandsystem.CommandException;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import adris.altoclef.commandsystem.CommandExecutor;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.control.InputControls;
import adris.altoclef.control.PlayerExtraController;
import adris.altoclef.control.SlotHandler;
import adris.altoclef.platform.AltoClefPlatform;
import adris.altoclef.trackers.TrackerManager;
import adris.altoclef.trackers.BlockTracker;
import adris.altoclef.trackers.EntityTracker;
import adris.altoclef.trackers.MiscBlockTracker;
import adris.altoclef.trackers.SimpleChunkTracker;
import adris.altoclef.trackers.storage.ContainerSubTracker;
import adris.altoclef.trackers.storage.ItemStorageTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;

/**
 * Temporary NeoForge-safe AltoClef core holder.
 * This class will expand toward upstream AltoClef as MojMap modules are ported.
 */
public class AltoClef {
    private final Settings settings = new Settings();
    private final BotBehaviour behaviour = new BotBehaviour();
    private final AltoClefPlatform platform;
    private final CommandExecutor commandExecutor = new CommandExecutor(this);
    private final TaskRunner taskRunner = new TaskRunner(this);
    private final UserTaskChain userTaskChain = new UserTaskChain(taskRunner);
    private final MobDefenseChain mobDefenseChain = new MobDefenseChain(taskRunner);
    private final FoodChain foodChain = new FoodChain(taskRunner);
    private final DeathMenuChain deathMenuChain = new DeathMenuChain(taskRunner);
    private final MLGBucketFallChain mlgBucketChain = new MLGBucketFallChain(taskRunner);
    private final WorldSurvivalChain worldSurvivalChain = new WorldSurvivalChain(taskRunner);
    private final PlayerInteractionFixChain playerInteractionFixChain = new PlayerInteractionFixChain(taskRunner);
    private final KillAura killAura = new KillAura();
    private final AltoClefSettings extraBaritoneSettings = new AltoClefSettings();
    private final InputControls inputControls = new InputControls();
    private final PlayerExtraController controllerExtras = new PlayerExtraController(this);
    private final SlotHandler slotHandler = new SlotHandler(this);
    private final TrackerManager trackerManager = new TrackerManager(this);
    private final EntityTracker entityTracker = new EntityTracker(trackerManager);
    private final BlockTracker blockTracker = new BlockTracker(this, trackerManager);
    private final MiscBlockTracker miscBlockTracker = new MiscBlockTracker(this);
    private final SimpleChunkTracker chunkTracker = new SimpleChunkTracker();
    private ContainerSubTracker containerTracker;
    private final ItemStorageTracker itemStorage = new ItemStorageTracker(this, trackerManager, tracker -> containerTracker = tracker);

    public AltoClef(AltoClefPlatform platform) {
        this.platform = platform;
        Debug.jankModInstance = this;
        try {
            new AltoClefCommands(this);
        } catch (CommandException e) {
            Debug.logWarning("Failed to register commands: " + e.getMessage());
        }
    }

    public static boolean inGame() {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().level != null;
    }

    public Settings getModSettings() {
        return settings;
    }

    public BotBehaviour getBehaviour() {
        return behaviour;
    }

    public TaskRunner getTaskRunner() {
        return taskRunner;
    }

    public UserTaskChain getUserTaskChain() {
        return userTaskChain;
    }

    public CommandExecutor getCommandExecutor() {
        return commandExecutor;
    }

    public void runUserTask(Task task, Runnable onFinish) {
        userTaskChain.runTask(this, task, onFinish);
    }

    public void runUserTask(Task task, Runnable onFinish, boolean prioritizeOverMobDefense) {
        userTaskChain.runTask(this, task, onFinish, prioritizeOverMobDefense);
    }

    public InputControls getInputControls() {
        return inputControls;
    }

    public KillAura getKillAura() {
        return killAura;
    }

    public FoodChain getFoodChain() {
        return foodChain;
    }

    public MobDefenseChain getMobDefenseChain() {
        return mobDefenseChain;
    }

    public MLGBucketFallChain getMLGBucketChain() {
        return mlgBucketChain;
    }

    public WorldSurvivalChain getWorldSurvivalChain() {
        return worldSurvivalChain;
    }

    public PlayerInteractionFixChain getPlayerInteractionFixChain() {
        return playerInteractionFixChain;
    }

    public AltoClefSettings getExtraBaritoneSettings() {
        return extraBaritoneSettings;
    }

    public IBaritone getClientBaritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    public baritone.api.Settings getClientBaritoneSettings() {
        return BaritoneAPI.getSettings();
    }

    public PlayerExtraController getControllerExtras() {
        return controllerExtras;
    }

    public SlotHandler getSlotHandler() {
        return slotHandler;
    }

    public TrackerManager getTrackerManager() {
        return trackerManager;
    }

    public ItemStorageTracker getItemStorage() {
        return itemStorage;
    }

    public ContainerSubTracker getContainerTracker() {
        return containerTracker;
    }

    public EntityTracker getEntityTracker() {
        return entityTracker;
    }

    public BlockTracker getBlockTracker() {
        return blockTracker;
    }

    public MiscBlockTracker getMiscBlockTracker() {
        return miscBlockTracker;
    }

    public SimpleChunkTracker getChunkTracker() {
        return chunkTracker;
    }

    public MultiPlayerGameMode getController() {
        return Minecraft.getInstance().gameMode;
    }

    public LocalPlayer getPlayer() {
        return Minecraft.getInstance().player;
    }

    public ClientLevel getWorld() {
        return Minecraft.getInstance().level;
    }

    public boolean runBaritone(String command) {
        return platform.runBaritone(command);
    }

    public void stopPathing() {
        platform.stopPathing();
    }

    public void log(String message) {
        platform.log(message);
    }
}
