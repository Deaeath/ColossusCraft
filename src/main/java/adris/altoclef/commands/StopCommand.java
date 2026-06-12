package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;

public class StopCommand extends Command {

    public StopCommand() {
        super("stop", "Stop task runner");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        mod.getUserTaskChain().cancel(mod);
        // Halt the whole runner so background chains (mob defense, etc.) also stop.
        mod.getTaskRunner().disable();
        // Also switch off autonomous food gathering — otherwise, while you're still hungry, the
        // FoodChain immediately resumes hunting the moment anything re-enables the runner. Re-enable
        // with /autohunt on when you want it back.
        if (mod.getModSettings().isAutoCollectFood()) {
            mod.getModSettings().setAutoCollectFood(false);
            mod.log("Stopped. (Auto-hunt turned OFF; re-enable with /autohunt on.)");
        }
        mod.stopPathing();
        finish();
    }
}
