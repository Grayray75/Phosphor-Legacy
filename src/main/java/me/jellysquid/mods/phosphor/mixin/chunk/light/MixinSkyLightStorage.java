package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.light.SectionLightStorageAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.SkyLightStorageMapAccess;
import me.jellysquid.mods.phosphor.common.util.math.ChunkSectionPosHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.SkyLightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(SkyLightStorage.class)
public abstract class MixinSkyLightStorage {
    /**
     * An optimized implementation which avoids constantly unpacking and repacking integer coordinates.
     *
     * @reason Use faster implementation
     * @author JellySquid
     */
    @SuppressWarnings({"ConstantConditions", "unchecked"})
    @Overwrite
    public int getLightOrDefault(long pos) {
        int posX = BlockPos.unpackX(pos);
        int posY = BlockPos.unpackY(pos);
        int posZ = BlockPos.unpackZ(pos);

        int chunkX = SectionPos.toChunk(posX);
        int chunkY = SectionPos.toChunk(posY);
        int chunkZ = SectionPos.toChunk(posZ);

        long chunk = SectionPos.asLong(chunkX, chunkY, chunkZ);

        SkyLightStorage.StorageMap data = ((SectionLightStorageAccess<SkyLightStorage.StorageMap>) this).bridge$getStorageUncached();

        int h = ((SkyLightStorageMapAccess) (Object) data).getHeightMap().get(SectionPos.toSectionColumnPos(chunk));

        if (h != ((SkyLightStorageMapAccess) (Object) data).getDefaultHeight() && chunkY < h) {
            NibbleArray array = data.getArray(chunk);

            if (array == null) {
                posY &= -16;

                while (array == null) {
                    ++chunkY;

                    if (chunkY >= h) {
                        return 15;
                    }

                    chunk = ChunkSectionPosHelper.updateYLong(chunk, chunkY);
                    posY += 16;
                    array = data.getArray(chunk);
                }
            }

            return array.get(
                    SectionPos.mask(posX),
                    SectionPos.mask(posY),
                    SectionPos.mask(posZ)
            );
        } else {
            return 15;
        }
    }
}
