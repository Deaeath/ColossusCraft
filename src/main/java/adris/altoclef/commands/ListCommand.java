package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;

public class ListCommand extends Command {
    public ListCommand() {
        super("list", "List obtainable item ids");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        mod.log("Obtainable ids: " + String.join(", ", TaskCatalogue.resourceNames()));
        finish();
    }
}
