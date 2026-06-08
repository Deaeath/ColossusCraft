package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.construction.ConstructNetherPortalTask;
import adris.altoclef.tasks.container.SmeltInFurnaceTask;
import adris.altoclef.tasks.entity.KillAndLootTask;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.tasks.speedrun.BeatMinecraftTask;
import adris.altoclef.tasks.speedrun.KillEnderDragonTask;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Items;

public class TestCommand extends Command {
    public TestCommand() throws CommandException {
        super("test", "Run AltoClef port smoke tests", new Arg(String.class, "test", "", 0));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String name = parser.get(String.class);
        switch (name) {
            case "" -> Debug.logWarning("Tests: food, stacked, smelt, blaze, portal, nether, overworld, gamer, dragon");
            case "food" -> mod.runUserTask(new CollectFoodTask(20), this::finish);
            case "stacked" -> mod.runUserTask(new EquipArmorTask(
                    new ItemTarget(Items.DIAMOND_CHESTPLATE, 1),
                    new ItemTarget(Items.DIAMOND_LEGGINGS, 1),
                    new ItemTarget(Items.DIAMOND_HELMET, 1),
                    new ItemTarget(Items.DIAMOND_BOOTS, 1)), this::finish);
            case "smelt" -> mod.runUserTask(new SmeltInFurnaceTask(new ItemTarget(Items.IRON_INGOT, 4)), this::finish);
            case "blaze" -> mod.runUserTask(new KillAndLootTask("blaze", new ItemTarget(Items.BLAZE_ROD, 7)), this::finish);
            case "portal" -> mod.runUserTask(new ConstructNetherPortalTask(), this::finish);
            case "nether" -> mod.runUserTask(new DefaultGoToDimensionTask(Dimension.NETHER), this::finish);
            case "overworld" -> mod.runUserTask(new DefaultGoToDimensionTask(Dimension.OVERWORLD), this::finish);
            case "gamer" -> mod.runUserTask(new BeatMinecraftTask(), this::finish);
            case "dragon", "dragon-old" -> mod.runUserTask(new KillEnderDragonTask(), this::finish);
            case "netherite" -> mod.runUserTask(TaskCatalogue.getSquashedItemTask(
                    new ItemTarget(Items.NETHERITE_PICKAXE, 1),
                    new ItemTarget(Items.NETHERITE_SWORD, 1),
                    new ItemTarget(Items.NETHERITE_HELMET, 1),
                    new ItemTarget(Items.NETHERITE_CHESTPLATE, 1),
                    new ItemTarget(Items.NETHERITE_LEGGINGS, 1),
                    new ItemTarget(Items.NETHERITE_BOOTS, 1)), this::finish);
            default -> {
                Debug.logWarning("Test not found: " + name);
                finish();
            }
        }
    }
}
