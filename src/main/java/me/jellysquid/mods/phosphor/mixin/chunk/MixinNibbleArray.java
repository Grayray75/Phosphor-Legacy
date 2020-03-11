package me.jellysquid.mods.phosphor.mixin.chunk;

import net.minecraft.world.chunk.NibbleArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * An optimized implementation of ChunkNibbleArray which uses bit-banging instead of a conditional to select
 * the right bit index of a nibble.
 * <p>
 * TODO: Is it if faster to always initialize this with a dummy array and then copy-on-write?
 */
@Mixin(NibbleArray.class)
public abstract class MixinNibbleArray {
    @Shadow
    protected byte[] data;

    /**
     * @reason Avoid an additional branch.
     * @author JellySquid
     */
    @Overwrite
    private int getFromIndex(int idx) {
        if (this.data == null) {
            return 0;
        }

        int nibbleIdx = idx & 1;
        int byteIdx = idx >> 1;
        int shift = nibbleIdx << 2;

        return (this.data[byteIdx] >>> shift) & 15;
    }

    /**
     * @reason Avoid an additional branch.
     * @author JellySquid
     */
    @Overwrite
    private void setIndex(int idx, int value) {
        if (this.data == null) {
            this.data = new byte[2048];
        }

        int nibbleIdx = idx & 1;
        int byteIdx = idx >> 1;
        int shift = nibbleIdx << 2;

        int b = this.data[byteIdx];
        int ret = (b & ~(15 << shift)) | (value & 15) << shift;

        this.data[byteIdx] = (byte) ret;
    }


}
