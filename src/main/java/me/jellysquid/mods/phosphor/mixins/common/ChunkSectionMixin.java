package me.jellysquid.mods.phosphor.mixins.common;

import net.minecraft.world.chunk.ChunkNibbleStorage;
import net.minecraft.world.chunk.WorldChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WorldChunkSection.class)
public abstract class ChunkSectionMixin {
    @Shadow
    private ChunkNibbleStorage skyLight;

    @Shadow
    private int nonAirBlockCount;

    @Shadow
    private ChunkNibbleStorage blockLight;

    private int lightRefCount = -1;

    /**
     * @author JellySquid
     * @reason Reset lightRefCount on call
     */
    @Overwrite
    public void setSkyLight(int x, int y, int z, int value) {
        this.skyLight.set(x, y, z, value);
        this.lightRefCount = -1;
    }

    /**
     * @author JellySquid
     * @reason Reset lightRefCount on call
     */
    @Overwrite
    public void setBlockLight(int x, int y, int z, int value) {
        this.blockLight.set(x, y, z, value);
        this.lightRefCount = -1;
    }

    /**
     * @author JellySquid
     * @reason Reset lightRefCount on call
     */
    @Overwrite
    public void setBlockLightStorage(ChunkNibbleStorage array) {
        this.blockLight = array;
        this.lightRefCount = -1;
    }

    /**
     * @author JellySquid
     * @reason Reset lightRefCount on call
     */
    @Overwrite
    public void setSkyLightStorage(ChunkNibbleStorage array) {
        this.skyLight = array;
        this.lightRefCount = -1;
    }


    /**
     * @author JellySquid
     * @reason Send light data to clients when lighting is non-trivial
     */
    @Overwrite
    public boolean isEmpty() {
        if (this.nonAirBlockCount != 0) {
            return false;
        }

        // -1 indicates the lightRefCount needs to be re-calculated
        if (this.lightRefCount == -1) {
            if (this.checkLightArrayEqual(this.skyLight, (byte) 0xFF) && this.checkLightArrayEqual(this.blockLight, (byte) 0x00)) {
                this.lightRefCount = 0; // Lighting is trivial, don't send to clients
            }
            else {
                this.lightRefCount = 1; // Lighting is not trivial, send to clients
            }
        }

        return this.lightRefCount == 0;
    }

    private boolean checkLightArrayEqual(ChunkNibbleStorage storage, byte val) {
        if (storage == null) {
            return true;
        }

        byte[] arr = storage.getData();

        for (byte b : arr) {
            if (b != val) {
                return false;
            }
        }

        return true;
    }
}
