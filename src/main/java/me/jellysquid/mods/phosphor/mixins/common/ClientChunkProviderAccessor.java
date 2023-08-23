package me.jellysquid.mods.phosphor.mixins.common;

import net.minecraft.util.collection.LongObjectStorage;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ClientChunkProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientChunkProvider.class)
public interface ClientChunkProviderAccessor {
    @Accessor("chunkStorage")
    LongObjectStorage<Chunk> getChunkStorage();
}
