package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.commandsystem.ItemList;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class EquipCommand extends Command {
    public EquipCommand() throws CommandException {
        super("equip", "Equip armor", new Arg(ItemList.class, "[armors]"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        ItemTarget[] items;
        if (parser.getArgUnits().length == 1) {
            items = switch (parser.getArgUnits()[0].toLowerCase()) {
                case "leather" -> armor(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
                case "iron" -> armor(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
                case "gold" -> armor(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
                case "diamond" -> armor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
                case "netherite" -> armor(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
                default -> parser.get(ItemList.class).items;
            };
        } else {
            items = parser.get(ItemList.class).items;
        }
        for (ItemTarget target : items) {
            for (Item item : target.getMatches()) {
                if (!(item instanceof ArmorItem)) {
                    throw new CommandException("You must provide armor items.");
                }
            }
        }
        mod.runUserTask(new EquipArmorTask(items), this::finish);
    }

    private static ItemTarget[] armor(Item helmet, Item chest, Item legs, Item boots) {
        return new ItemTarget[]{new ItemTarget(helmet), new ItemTarget(chest), new ItemTarget(legs), new ItemTarget(boots)};
    }
}
