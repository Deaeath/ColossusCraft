package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import net.minecraft.client.Minecraft;

public class SetGammaCommand extends Command {
    public SetGammaCommand() throws CommandException {
        super("gamma", "Set brightness", new Arg(Double.class, "gamma", 1.0, 0));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        double value = parser.get(Double.class);
        Minecraft.getInstance().options.gamma().set(value);
        mod.log("Gamma set to " + value);
        finish();
    }
}
