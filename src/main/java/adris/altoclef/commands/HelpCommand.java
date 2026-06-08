package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;

import java.util.Comparator;

public class HelpCommand extends Command {
    public HelpCommand() {
        super("help", "Lists all commands");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        StringBuilder message = new StringBuilder("Commands:");
        mod.getCommandExecutor().allCommands().stream()
                .sorted(Comparator.comparing(Command::getName))
                .forEach(command -> message.append(" @")
                        .append(command.getHelpRepresentation())
                        .append(" - ")
                        .append(command.getDescription())
                        .append(";"));
        mod.log(message.toString());
        finish();
    }
}
