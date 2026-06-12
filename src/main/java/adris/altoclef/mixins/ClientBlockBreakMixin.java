package adris.altoclef.mixins;

import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.BlockBreakingCancelEvent;
import adris.altoclef.eventbus.events.BlockBreakingEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Publishes block-breaking progress + cancel events so PlayerExtraController can answer
 * isBreakingBlock()/getBreakingBlockPos() (used by the tool auto-swap).
 */
@Mixin(MultiPlayerGameMode.class)
public final class ClientBlockBreakMixin {
    // Baritone triggers a break-cancel every other frame, so require 2 frames before announcing a real cancel.
    private static int _breakCancelFrames;

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"))
    private void onBreakUpdate(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> ci) {
        if (Minecraft.getInstance().gameMode instanceof ClientBlockBreakAccessor breakAccessor) {
            _breakCancelFrames = 2;
            EventBus.publish(new BlockBreakingEvent(pos, breakAccessor.getCurrentBreakingProgress()));
        }
    }

    @Inject(method = "stopDestroyBlock", at = @At("HEAD"))
    private void cancelBlockBreaking(CallbackInfo ci) {
        if (_breakCancelFrames-- == 0) {
            EventBus.publish(new BlockBreakingCancelEvent());
        }
    }
}
