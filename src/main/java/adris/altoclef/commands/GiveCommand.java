package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.entity.GiveItemToPlayerTask;
import adris.altoclef.util.ItemTarget;

public class GiveCommand extends Command {
    public GiveCommand() throws CommandException {
        super("give", "Collect item and give it to player",
                new Arg(String.class, "username"),
                new Arg(String.class, "item"),
                new Arg(Integer.class, "count", 1, 2));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String username = parser.get(String.class);
        String item = parser.get(String.class);
        int count = parser.get(Integer.class);
        ItemTarget target = TaskCatalogue.getItemTarget(item, count);
        if (target.isEmpty()) {
            mod.log("Unknown item: " + item);
            finish();
            return;
        }
        mod.runUserTask(new GiveItemToPlayerTask(username, target), this::finish);
    }
}
