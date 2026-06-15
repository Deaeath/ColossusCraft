package adris.altoclef.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows the player to open chat (T) or command (/) while any container screen is open,
 * so they can type AltoClef commands (e.g. /stop) without closing the container first.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenChatMixin extends Screen {
    protected ContainerScreenChatMixin() {
        super(null);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        // T → open chat; / → open chat pre-filled with "/"
        boolean chatKey = mc.options.keyChat.matches(keyCode, scanCode);
        boolean cmdKey = mc.options.keyCommand.matches(keyCode, scanCode);
        if (chatKey || cmdKey) {
            mc.setScreen(new ChatScreen(cmdKey ? "/" : ""));
            ci.setReturnValue(true);
        }
    }
}
