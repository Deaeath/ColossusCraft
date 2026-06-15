package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;

/**
 * /fortune on|off
 *
 * When on, DestroyBlockTask routes fortune pickaxes to ore blocks and keeps
 * non-fortune pickaxes on everything else (cobblestone, dirt, etc.).
 */
public class FortuneCommand extends Command {

    public FortuneCommand() throws CommandException {
        super("fortune", "Preserve fortune pickaxe: only use it on ores");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String[] units = parser.getArgUnits();
        boolean enable;
        if (units.length == 0) {
            // No argument → toggle
            enable = !mod.getBehaviour().shouldPreserveFortune();
        } else {
            String arg = units[0].toLowerCase();
            enable = arg.equals("on") || arg.equals("true") || arg.equals("1");
        }
        mod.getBehaviour().setPreserveFortune(enable);
        mod.log("Fortune preserve: " + (enable ? "ON (fortune pickaxe reserved for ores)" : "OFF"));
        finish();
    }
}
