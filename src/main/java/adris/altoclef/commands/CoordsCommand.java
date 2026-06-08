package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.util.helpers.WorldHelper;

public class CoordsCommand extends Command {
    public CoordsCommand() {
        super("coords", "Get bot coordinates");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        if (mod.getPlayer() == null) {
            mod.log("No player.");
        } else {
            mod.log("Coords: " + mod.getPlayer().blockPosition().toShortString() + " dim=" + WorldHelper.getCurrentDimension());
        }
        finish();
    }
}
