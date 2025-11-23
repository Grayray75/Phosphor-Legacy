package me.jellysquid.mods.phosphor.mixins.common;

import me.jellysquid.mods.phosphor.api.IChunkLightingData;
import me.jellysquid.mods.phosphor.api.ILightingEngineProvider;
import me.jellysquid.mods.phosphor.mod.world.lighting.LightingHooks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.storage.AnvilChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilChunkStorage.class)
public abstract class ThreadedAnvilChunkStorageMixin {
    /**
     * Injects into the head of saveChunk() to forcefully process all pending light updates. Fail-safe.
     *
     * @author JellySquid
     */
    @Inject(method = "saveChunk", at = @At("HEAD"))
    private void onConstructed(World world, WorldChunk chunkIn, CallbackInfo callbackInfo) {
        ((ILightingEngineProvider) world).getLightingEngine().processLightUpdates();
    }

    /**
     * Injects the deserialization logic for chunk data on load so we can extract whether or not we've populated light yet.
     *
     * @author JellySquid
     */
    @Inject(method = "createChunkFromNbt", at = @At("RETURN"))
    private void onReadChunkFromNBT(World world, NbtCompound compound, CallbackInfoReturnable<WorldChunk> cir) {
        WorldChunk chunk = cir.getReturnValue();

        LightingHooks.readNeighborLightChecksFromNBT(chunk, compound);

        ((IChunkLightingData) chunk).setLightInitialized(compound.getBoolean("LightPopulated"));

    }

    /**
     * Injects the serialization logic for chunk data on save so we can store whether or not we've populated light yet.
     *
     * @author JellySquid
     */
    @Inject(method = "writeChunkToNbt", at = @At("RETURN"))
    private void onWriteChunkToNBT(WorldChunk chunk, World world, NbtCompound compound, CallbackInfo ci) {
        LightingHooks.writeNeighborLightChecksToNBT(chunk, compound);

        compound.putBoolean("LightPopulated", ((IChunkLightingData) chunk).isLightInitialized());
    }
}
