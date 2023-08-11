package me.jellysquid.mods.phosphor.mixins.lighting.common;

import me.jellysquid.mods.phosphor.api.IChunkLightingData;
import me.jellysquid.mods.phosphor.api.ILightingEngineProvider;
import me.jellysquid.mods.phosphor.mod.world.lighting.LightingHooks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class MixinAnvilChunkLoader {
    /**
     * Injects into the head of saveChunk() to forcefully process all pending light updates. Fail-safe.
     *
     * @author JellySquid
     */
    @Inject(method = "writeChunk", at = @At("HEAD"))
    private void onConstructed(World world, Chunk chunkIn, CallbackInfo callbackInfo) {
        ((ILightingEngineProvider) world).getLightingEngine().processLightUpdates();
    }

    /**
     * Injects the deserialization logic for chunk data on load so we can extract whether or not we've populated light yet.
     *
     * @author JellySquid
     */
    @Inject(method = "getChunk", at = @At("RETURN"))
    private void onReadChunkFromNBT(World world, NbtCompound compound, CallbackInfoReturnable<Chunk> cir) {
        Chunk chunk = cir.getReturnValue();

        LightingHooks.readNeighborLightChecksFromNBT(chunk, compound);

        ((IChunkLightingData) chunk).setLightInitialized(compound.getBoolean("LightPopulated"));

    }

    /**
     * Injects the serialization logic for chunk data on save so we can store whether or not we've populated light yet.
     *
     * @author JellySquid
     */
    @Inject(method = "putChunk", at = @At("RETURN"))
    private void onWriteChunkToNBT(Chunk chunk, World world, NbtCompound compound, CallbackInfo ci) {
        LightingHooks.writeNeighborLightChecksToNBT(chunk, compound);

        compound.putBoolean("LightPopulated", ((IChunkLightingData) chunk).isLightInitialized());
    }
}
