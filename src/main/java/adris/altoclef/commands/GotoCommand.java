package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.commandsystem.GotoTarget;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.GetToXZTask;
import adris.altoclef.tasks.movement.GetToYTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class GotoCommand extends Command {
    public GotoCommand() throws CommandException {
        super("goto", "Travel to coordinates with Baritone",
                new Arg(GotoTarget.class, "[x y z dimension]/[x z dimension]/[y dimension]/[dimension]"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        GotoTarget target = parser.get(GotoTarget.class);
        Task task = switch (target.getType()) {
            case XYZ -> new GetToBlockTask(new BlockPos(target.getX(), target.getY(), target.getZ()), target.getDimension());
            case XZ -> new GetToXZTask(target.getX(), target.getZ(), target.getDimension());
            case Y -> new GetToYTask(target.getY(), target.getDimension());
            case NONE -> target.hasDimension() ? new DefaultGoToDimensionTask(target.getDimension()) : null;
        };
        if (task == null) mod.log("No coordinates supplied.");
        else mod.runUserTask(task, this::finish);
    }
}
