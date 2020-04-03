package me.jellysquid.mods.phosphor.common.chunk;

import net.minecraft.util.math.shapes.VoxelShape;

public interface BlockStateCacheAccess {
    VoxelShape[] getExtrudedFaces();

    int getLightSubtracted();
}
