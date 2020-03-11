package me.jellysquid.mods.phosphor.common.chunk;

import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.LightDataMap;

public interface ExtendedSectionLightStorage<M extends LightDataMap<M>> extends ExtendedGenericLightStorage {
    /**
     * Bridge method to LightStorage#getDataForChunk(M, long).
     */
    NibbleArray bridge$getDataForChunk(M data, long chunk);

    /**
     * Bridge method to LightStorage#getStorageUncached().
     */
    M bridge$getStorageUncached();
}
