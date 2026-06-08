package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.util.helpers.ConfigHelper;

import java.util.StringJoiner;

public class CustomCommand extends Command {
    private static CustomTaskConfig config;

    static {
        ConfigHelper.loadConfig("configs/CustomTasks.json", CustomTaskConfig::new, CustomTaskConfig.class, loaded -> config = loaded);
    }

    public static CustomTaskConfig getConfig() {
        return config;
    }

    public CustomCommand() throws CommandException {
        super(config.prefix, "Run configured custom task", new Arg(String.class, "task name"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String name = parser.get(String.class);
        CustomTaskConfig.CustomTaskEntry task = findTask(name);
        if (task == null) {
            throw new CommandException("Custom task not found: " + name);
        }

        StringJoiner commands = new StringJoiner(";");
        for (CustomTaskConfig.CustomTaskEntry.CustomSubTaskEntry subTask : task.tasks) {
            if (subTask.command == null || subTask.command.isBlank()) {
                continue;
            }
            StringBuilder line = new StringBuilder(subTask.command);
            String args = formatArgs(subTask.parameters);
            if (!args.isBlank()) {
                line.append(' ').append(args);
            }
            commands.add(line.toString());
        }
        mod.getCommandExecutor().executeWithPrefix(commands.toString());
        finish();
    }

    private static CustomTaskConfig.CustomTaskEntry findTask(String name) {
        for (CustomTaskConfig.CustomTaskEntry task : config.customTasks) {
            if (task.name != null && task.name.equalsIgnoreCase(name)) {
                return task;
            }
        }
        return null;
    }

    private static String formatArgs(String[][] parameters) {
        if (parameters == null || parameters.length == 0) {
            return "";
        }
        StringJoiner groups = new StringJoiner(",");
        for (String[] group : parameters) {
            if (group == null || group.length == 0) {
                continue;
            }
            StringJoiner items = new StringJoiner(" ");
            for (String value : group) {
                if (value != null && !value.isBlank()) {
                    items.add(value);
                }
            }
            if (items.length() != 0) {
                groups.add(items.toString());
            }
        }
        return groups.toString();
    }
}
