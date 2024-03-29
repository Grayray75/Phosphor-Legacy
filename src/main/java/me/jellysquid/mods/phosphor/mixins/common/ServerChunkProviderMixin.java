package me.jellysquid.mods.phosphor.mixins.common;

import me.jellysquid.mods.phosphor.api.ILightingEngineProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.ServerChunkProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(ServerChunkProvider.class)
public abstract class ServerChunkProviderMixin {
    @Shadow
    @Final
    private ServerWorld world;

    @Shadow
    @Final
    private Set<Long> chunksToUnload;

    /**
     * Injects a callback into the start of saveChunks(boolean) to force all light updates to be processed before saving.
     *
     * @author JellySquid
     */
    @Inject(method = "saveAllChunks", at = @At("HEAD"))
    private void onSaveChunks(boolean all, CallbackInfoReturnable<Boolean> cir) {
        ((ILightingEngineProvider) this.world).getLightingEngine().processLightUpdates();
    }

    /**
     * Injects a callback into the start of the onTick() method to process all pending light updates. This is not necessarily
     * required, but we don't want our work queues getting too large.
     *
     * @author JellySquid
     */
    @Inject(method = "tickChunks", at = @At("HEAD"))
    private void onTick(CallbackInfoReturnable<Boolean> cir) {
        if (!this.world.savingDisabled) {
            if (!this.chunksToUnload.isEmpty()) {
                ((ILightingEngineProvider) this.world).getLightingEngine().processLightUpdates();
            }
        }
    }
}
