package me.jellysquid.mods.phosphor.api;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

public interface ILightingEngine {
    void scheduleLightUpdate(LightType lightType, BlockPos pos);

    void processLightUpdates();

    void processLightUpdatesForType(LightType lightType);
}
