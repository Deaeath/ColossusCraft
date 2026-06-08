package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.movement.FollowPlayerTask;

public class FollowCommand extends Command {
    public FollowCommand() throws CommandException {
        super("follow", "Follow a player", new Arg(String.class, "username"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        mod.runUserTask(new FollowPlayerTask(parser.get(String.class)), this::finish);
    }
}
