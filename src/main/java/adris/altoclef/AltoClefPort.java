package adris.altoclef;

import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.ClientTickEvent;
import adris.altoclef.eventbus.events.PortTickEvent;
import adris.altoclef.platform.AltoClefPlatform;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * NeoForge-safe shell for the full AltoClef port.
 * Platform-bound code is kept behind AltoClefPlatform while upstream modules are remapped.
 */
public final class AltoClefPort {
    private final AltoClefPlatform platform;
    private final AltoClef altoClef;
    private boolean running;

    public AltoClefPort(AltoClefPlatform platform) {
        this.platform = platform;
        this.altoClef = new AltoClef(platform);
    }

    public void start() {
        boolean wasRunning = running;
        running = true;
        altoClef.getTaskRunner().enable();
        if (!wasRunning) {
            platform.log("ColossusCraft core online");
        }
    }

    public void stop() {
        running = false;
        altoClef.getTaskRunner().disable();
        platform.stopPathing();
        platform.log("ColossusCraft core stopped");
    }

    public void tick() {
        if (running && platform.playerReady()) {
            altoClef.getInputControls().onTickPre();
            altoClef.getTrackerManager().tick();
            altoClef.getMiscBlockTracker().tick();
            EventBus.publish(new ClientTickEvent());
            EventBus.publish(new PortTickEvent(platform.tickCount()));
            altoClef.getTaskRunner().tick();
            altoClef.getInputControls().onTickPost();
        }
    }

    public boolean running() {
        return running;
    }

    public List<String> parseCommand(String line) {
        return ArgParser.splitLineIntoKeywords(line);
    }

    public AltoClef core() {
        return altoClef;
    }

    public boolean runItemTask(String itemName, int count) {
        // Try the catalogue first (handles crafting, smelting, farming, etc.)
        if (TaskCatalogue.taskExists(itemName)) {
            Task task = TaskCatalogue.getItemTask(itemName, count);
            start();
            altoClef.getUserTaskChain().runTask(altoClef, task, () -> platform.log("Collected " + itemName));
            return true;
        }
        // Fall back to registry lookup — handles any vanilla item by its registry name
        // e.g. "elytra", "totem_of_undying", "nether_star"
        String registryName = itemName.contains(":") ? itemName : "minecraft:" + itemName;
        Item registryItem = BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.parse(registryName))
                .orElse(null);
        if (registryItem == null || registryItem == net.minecraft.world.item.Items.AIR) {
            platform.log("Unknown item: " + itemName);
            return false;
        }
        ItemTarget target = new ItemTarget(registryItem, count);
        start();
        altoClef.getUserTaskChain().runTask(altoClef, new CollectItemTask(target), () -> platform.log("Collected " + itemName));
        return true;
    }

    public void executeCommand(String line) {
        start();
        if (!line.startsWith(altoClef.getModSettings().getCommandPrefix())) {
            line = altoClef.getModSettings().getCommandPrefix() + line;
        }
        altoClef.getCommandExecutor().execute(line);
    }
}
