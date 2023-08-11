package me.jellysquid.mods.phosphor.api;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

public interface IChunkLighting {
    int getCachedLightFor(LightType lightType, BlockPos pos);
}
