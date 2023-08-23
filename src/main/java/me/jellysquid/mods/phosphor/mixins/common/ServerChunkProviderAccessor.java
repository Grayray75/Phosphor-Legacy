package me.jellysquid.mods.phosphor.mixins.common;

import net.minecraft.util.collection.LongObjectStorage;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ServerChunkProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkProvider.class)
public interface ServerChunkProviderAccessor {
    @Accessor("chunkMap")
    LongObjectStorage<Chunk> getChunkStorage();
}
