package me.jellysquid.mods.phosphor.mixins.common;

import me.jellysquid.mods.phosphor.api.ILightingEngineProvider;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkDataS2CPacket.class)
public abstract class ChunkDataS2CPacketMixin {
    /**
     * Injects a callback into the constructor of ChunkDataS2CPacketMixin to force light updates to be
     * processed before creating the client payload.
     *
     * @author JellySquid
     */
    @Inject(method = "createExtraData", at = @At("HEAD"))
    private static void onCalculateChunkSize(Chunk chunk, boolean load, boolean notNether, int i, CallbackInfoReturnable<ChunkDataS2CPacket.ExtraData> cir) {
        ((ILightingEngineProvider) chunk).getLightingEngine().processLightUpdates();
    }
}
