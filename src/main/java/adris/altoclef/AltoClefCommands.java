package adris.altoclef;

import adris.altoclef.commands.CoordsCommand;
import adris.altoclef.commands.CoverWithBlocksCommand;
import adris.altoclef.commands.CoverWithSandCommand;
import adris.altoclef.commands.CustomCommand;
import adris.altoclef.commands.DepositCommand;
import adris.altoclef.commands.EquipCommand;
import adris.altoclef.commands.FoodCommand;
import adris.altoclef.commands.FortuneCommand;
import adris.altoclef.commands.FollowCommand;
import adris.altoclef.commands.GamerCommand;
import adris.altoclef.commands.GetCommand;
import adris.altoclef.commands.GiveCommand;
import adris.altoclef.commands.GotoCommand;
import adris.altoclef.commands.HeroCommand;
import adris.altoclef.commands.KillCommand;
import adris.altoclef.commands.HelpCommand;
import adris.altoclef.commands.IdleCommand;
import adris.altoclef.commands.InventoryCommand;
import adris.altoclef.commands.ListCommand;
import adris.altoclef.commands.LocateStructureCommand;
import adris.altoclef.commands.MarvionCommand;
import adris.altoclef.commands.MeatCommand;
import adris.altoclef.commands.PunkCommand;
import adris.altoclef.commands.ReloadSettingsCommand;
import adris.altoclef.commands.SetGammaCommand;
import adris.altoclef.commands.StashCommand;
import adris.altoclef.commands.StatusCommand;
import adris.altoclef.commands.StopCommand;
import adris.altoclef.commands.TestCommand;
import adris.altoclef.commandsystem.CommandException;

public class AltoClefCommands {
    public AltoClefCommands(AltoClef mod) throws CommandException {
        mod.getCommandExecutor().registerNewCommand(
                new HelpCommand(),
                new CustomCommand(),
                new GetCommand(),
                new FollowCommand(),
                new GiveCommand(),
                new DepositCommand(),
                new StashCommand(),
                new EquipCommand(),
                new GotoCommand(),
                new CoverWithSandCommand(),
                new CoverWithBlocksCommand(),
                new IdleCommand(),
                new CoordsCommand(),
                new StatusCommand(),
                new InventoryCommand(),
                new FoodCommand(),
                new MeatCommand(),
                new PunkCommand(),
                new ListCommand(),
                new LocateStructureCommand(),
                new ReloadSettingsCommand(),
                new GamerCommand(),
                new MarvionCommand(),
                new HeroCommand(),
                new KillCommand(),
                new TestCommand(),
                new SetGammaCommand(),
                new StopCommand(),
                new FortuneCommand()
        );
    }
}
