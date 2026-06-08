package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.tasksystem.Task;

public class StatusCommand extends Command {
    public StatusCommand() {
        super("status", "Get current task status");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        Task task = mod.getUserTaskChain().getCurrentTask();
        mod.log(task == null ? "No task running." : "Task: " + task);
        finish();
    }
}
