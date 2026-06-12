package adris.altoclef.platform;

import baritone.api.BaritoneAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class NeoForgeAltoClefPlatform implements AltoClefPlatform {
    private long ticks;

    @Override
    public boolean playerReady() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.getConnection() != null;
    }

    @Override
    public long tickCount() {
        return ticks++;
    }

    @Override
    public void log(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        } else {
            System.out.println("[ColossusCraft] " + message);
        }
    }

    @Override
    public void stopPathing() {
        runBaritone("stop");
    }

    @Override
    public boolean runBaritone(String command) {
        // Drive Baritone through its real public API (un-obfuscated jar). The command manager
        // executes the same strings the chat would ("goto x y z", "mine <block>", "explore", "stop").
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(command);
        } catch (Throwable t) {
            System.out.println("[ColossusCraft] Baritone command failed: " + command + " -> " + t);
            return false;
        }
    }
}
