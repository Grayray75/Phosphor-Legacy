package me.jellysquid.mods.phosphor.mod.world;

import me.jellysquid.mods.phosphor.mixins.common.PaletteContainerAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

public class BlockStateHelper {
    private static final BlockState DEFAULT_BLOCK_STATE = Blocks.AIR.getDefaultState();

    // Avoids some additional logic in Chunk#getBlockState... 0 is always air
    public static BlockState posToState(final BlockPos pos, final Chunk chunk) {
        return posToState(pos, chunk.getBlockStorage()[pos.getY() >> 4]);
    }

    public static BlockState posToState(final BlockPos pos, final ChunkSection section) {
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();

        if (section != Chunk.EMPTY) {
            int i = ((PaletteContainerAccessor) section.getBlockData()).getStorage().get((y & 15) << 8 | (z & 15) << 4 | x & 15);

            if (i != 0) {
                BlockState state = ((PaletteContainerAccessor) section.getBlockData()).getPalette().getStateForId(i);

                if (state != null) {
                    return state;
                }
            }
        }

        return DEFAULT_BLOCK_STATE;
    }
}
