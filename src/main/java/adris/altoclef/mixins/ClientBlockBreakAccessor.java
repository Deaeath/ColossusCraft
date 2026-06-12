package adris.altoclef.mixins;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the current block-breaking progress (0..1) so we can report it on break events. */
@Mixin(MultiPlayerGameMode.class)
public interface ClientBlockBreakAccessor {
    @Accessor("destroyProgress")
    float getCurrentBreakingProgress();
}
