package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;

public class ReloadSettingsCommand extends Command {
    public ReloadSettingsCommand() {
        super("reload_settings", "Reload settings");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        mod.log("Settings reload not needed: NeoForge port settings are in-memory.");
        finish();
    }
}
