package me.jellysquid.mods.phosphor.common.chunk.light;

import net.minecraft.block.BlockState;
import net.minecraft.world.lighting.LevelBasedGraph;

public interface LevelBasedGraphExtended {
    /**
     * Mirrors {@link LevelBasedGraph#notifyNeighbors(long, int, boolean)}, but allows a block state to be passed to
     * prevent subsequent lookup later.
     */
    void notifyNeighbors(long sourceId, BlockState sourceState, long targetId, int level, boolean decrease);

    /**
     * Copy of {@link LevelBasedGraph#getEdgeLevel(long, long, int)} but with an additional argument to pass the
     * block state belonging to {@param sourceId}.
     */
    int getEdgeLevel(long sourceId, BlockState sourceState, long targetId, int level);
}
