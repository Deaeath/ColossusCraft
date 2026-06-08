package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.commandsystem.ItemList;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;

public class GetCommand extends Command {

    public GetCommand() throws CommandException {
        super("get", "Get an item/resource", new Arg(ItemList.class, "items"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        ItemList items = parser.get(ItemList.class);
        Task task = items.items.length == 1
                ? TaskCatalogue.getItemTask(items.items[0])
                : TaskCatalogue.getSquashedItemTask(items.items);
        if (items.items.length == 0) {
            mod.log("Missing item");
            finish();
            return;
        }
        mod.runUserTask(task, this::finish);
    }
}
