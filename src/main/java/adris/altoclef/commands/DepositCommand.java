package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.commandsystem.ItemList;
import adris.altoclef.tasks.container.StoreInOpenContainerTask;
import adris.altoclef.util.ItemTarget;

public class DepositCommand extends Command {
    public DepositCommand() throws CommandException {
        super("deposit", "Deposit items into open container", new Arg(ItemList.class, "items", null, 0));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        ItemList list = parser.get(ItemList.class);
        ItemTarget[] targets = list == null ? new ItemTarget[0] : list.items;
        mod.runUserTask(new StoreInOpenContainerTask(targets), this::finish);
    }
}
