package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.entity.KillPlayerTask;

public class PunkCommand extends Command {
    public PunkCommand() throws CommandException {
        super("punk", "Attack a player", new Arg(String.class, "username"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        mod.runUserTask(new KillPlayerTask(parser.get(String.class)), this::finish);
    }
}
