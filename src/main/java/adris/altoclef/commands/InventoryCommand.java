package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public class InventoryCommand extends Command {
    public InventoryCommand() throws CommandException {
        super("inventory", "Print inventory or count item", new Arg(String.class, "item", null, 1));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String item = parser.get(String.class);
        if (mod.getPlayer() == null) {
            mod.log("No player.");
            finish();
            return;
        }
        if (item == null) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (int i = 0; i < mod.getPlayer().getInventory().getContainerSize(); i++) {
                ItemStack stack = mod.getPlayer().getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    counts.merge(ItemHelper.toResourceName(stack), stack.getCount(), Integer::sum);
                }
            }
            mod.log("Inventory: " + counts);
        } else {
            Item[] matches = TaskCatalogue.getItemMatches(item);
            if (matches.length == 0) {
                mod.log("Unknown item: " + item);
            } else {
                mod.log(item + " count: " + mod.getItemStorage().getItemCount(matches));
            }
        }
        finish();
    }
}
