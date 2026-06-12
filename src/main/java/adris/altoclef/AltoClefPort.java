package adris.altoclef;

import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.ClientTickEvent;
import adris.altoclef.eventbus.events.PortTickEvent;
import adris.altoclef.platform.AltoClefPlatform;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;

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
        ItemTarget target = TaskCatalogue.getItemTarget(itemName, count);
        if (target.isEmpty()) {
            platform.log("Unknown item: " + itemName);
            return false;
        }
        Task task = TaskCatalogue.getItemTask(target);
        start();
        altoClef.getUserTaskChain().runTask(altoClef, task, () -> platform.log("Collected " + target));
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
