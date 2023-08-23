package me.jellysquid.mods.phosphor.mod.world;

import me.jellysquid.mods.phosphor.mixins.common.ClientChunkProviderAccessor;
import me.jellysquid.mods.phosphor.mixins.common.ServerChunkProviderAccessor;
import net.minecraft.util.collection.LongObjectStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ClientChunkProvider;
import net.minecraft.world.chunk.ServerChunkProvider;

public class ChunkHelper {
    public static Chunk getLoadedChunk(ChunkProvider chunkProvider, int x, int z) {
        if (chunkProvider instanceof ServerChunkProvider) {
            LongObjectStorage<Chunk> chunkStorage = ((ServerChunkProviderAccessor) chunkProvider).getChunkStorage();
            return chunkStorage.get(ChunkPos.getIdFromCoords(x, z));
        }
        if (chunkProvider instanceof ClientChunkProvider) {
            LongObjectStorage<Chunk> chunkStorage = ((ClientChunkProviderAccessor) chunkProvider).getChunkStorage();
            return chunkStorage.get(ChunkPos.getIdFromCoords(x, z));
        }

        // Fallback for other providers, hopefully this doesn't break...
        return chunkProvider.getChunk(x, z);
    }
}
