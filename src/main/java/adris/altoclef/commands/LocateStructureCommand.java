package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.trackers.LocateResultTracker;
import net.minecraft.client.Minecraft;

public class LocateStructureCommand extends Command {
    public LocateStructureCommand() throws CommandException {
        super("locate_structure", "Run vanilla locate structure command", new Arg(String.class, "structure"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String structure = parser.get(String.class);
        if (Minecraft.getInstance().getConnection() == null) {
            mod.log("No server connection.");
        } else {
            LocateResultTracker.clear();
            Minecraft.getInstance().getConnection().sendCommand("locate structure " + structure);
            mod.log("Sent /locate structure " + structure);
        }
        finish();
    }
}
