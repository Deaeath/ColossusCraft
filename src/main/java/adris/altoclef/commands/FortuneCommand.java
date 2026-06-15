package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * /cc fortune [on|off|status]
 *
 * Preserve-fortune mode: routes the fortune pickaxe to ore blocks only.
 * On all other blocks the bot equips a non-fortune tool (falls back to fortune
 * only if no other correct tool exists).
 */
public class FortuneCommand extends Command {

    public FortuneCommand() throws CommandException {
        super("fortune", "Preserve fortune pickaxe for ores only");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String[] units = parser.getArgUnits();
        String arg = units.length > 0 ? units[0].toLowerCase() : "";

        switch (arg) {
            case "status" -> {
                printStatus(mod);
            }
            case "on" -> {
                if (mod.getBehaviour().shouldPreserveFortune()) {
                    mod.log("Fortune preserve is already ON.");
                } else {
                    mod.getBehaviour().setPreserveFortune(true);
                    mod.log("Fortune preserve: ON");
                }
                printFortuneTools(mod);
            }
            case "off" -> {
                if (!mod.getBehaviour().shouldPreserveFortune()) {
                    mod.log("Fortune preserve is already OFF.");
                } else {
                    mod.getBehaviour().setPreserveFortune(false);
                    mod.log("Fortune preserve: OFF — fortune pickaxe will be used freely.");
                }
            }
            default -> {
                // No arg or unrecognised → toggle
                boolean enable = !mod.getBehaviour().shouldPreserveFortune();
                mod.getBehaviour().setPreserveFortune(enable);
                mod.log("Fortune preserve: " + (enable ? "ON" : "OFF"));
                if (enable) printFortuneTools(mod);
            }
        }
        finish();
    }

    private void printStatus(AltoClef mod) {
        boolean on = mod.getBehaviour().shouldPreserveFortune();
        mod.log("Fortune preserve: " + (on ? "ON" : "OFF"));
        printFortuneTools(mod);
    }

    private void printFortuneTools(AltoClef mod) {
        boolean found = false;
        for (Slot slot : Slot.getCurrentScreenSlots()) {
            if (!slot.isSlotInPlayerInventory()) continue;
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (!StorageHelper.hasFortuneEnchantment(stack)) continue;
            int maxDmg = stack.getMaxDamage();
            int dmg = stack.getDamageValue();
            String dur = maxDmg > 0
                    ? " (" + (maxDmg - dmg) + "/" + maxDmg + " durability)"
                    : "";
            String name = stack.getHoverName().getString();
            boolean equipped = slot.equals(PlayerSlot.getEquipSlot());
            mod.log("  Fortune tool: " + name + dur + (equipped ? " [equipped]" : ""));
            found = true;
        }
        if (!found) {
            mod.log("  (No fortune-enchanted tools in inventory)");
        }
    }
}
