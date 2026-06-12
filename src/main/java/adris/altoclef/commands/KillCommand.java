package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Hunt down and kill the nearest entity matching a registry id (e.g. "zombie", "minecraft:cow").
 */
public class KillCommand extends Command {
    public KillCommand() throws CommandException {
        super("kill", "Hunt and kill the nearest entity of a type", new Arg(String.class, "entity"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String raw = parser.get(String.class);
        String id = raw.contains(":") ? raw : "minecraft:" + raw;
        mod.runUserTask(new KillEntitiesTask(
                entity -> id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())
        ), this::finish, true);
    }
}
