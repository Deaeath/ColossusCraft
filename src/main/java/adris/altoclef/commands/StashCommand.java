package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.commandsystem.ItemList;
import adris.altoclef.tasks.container.StashInRangeTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.core.BlockPos;

import java.util.Arrays;

public class StashCommand extends Command {
    public StashCommand() {
        super("stash", "Deposit items in a stash region");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String[] args = parser.getArgUnits();
        if (args.length < 6) {
            throw new CommandException("Usage: stash x0 y0 z0 x1 y1 z1 [items...]");
        }
        BlockPos a = new BlockPos(parse(args[0]), parse(args[1]), parse(args[2]));
        BlockPos b = new BlockPos(parse(args[3]), parse(args[4]), parse(args[5]));
        ItemTarget[] targets = new ItemTarget[0];
        if (args.length > 6) {
            targets = ItemList.parseRemainder(String.join(" ", Arrays.copyOfRange(args, 6, args.length))).items;
        }
        mod.runUserTask(new StashInRangeTask(a, b, targets), this::finish);
    }

    private int parse(String arg) throws CommandException {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            throw new CommandException("Invalid coordinate: " + arg, e);
        }
    }
}
