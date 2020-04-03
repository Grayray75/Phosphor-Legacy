package me.jellysquid.mods.phosphor.common.chunk.light;

import net.minecraft.world.lighting.LightDataMap;

public interface SectionLightStorageAccess<M extends LightDataMap<M>> {
    /**
     * Bridge method to LightStorage#getStorageUncached().
     */
    M bridge$getStorageUncached();
}
