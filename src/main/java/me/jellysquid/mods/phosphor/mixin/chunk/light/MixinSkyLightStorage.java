package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.ExtendedSectionLightStorage;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedSkyLightStorage;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedSkyLightStorageMap;
import me.jellysquid.mods.phosphor.common.util.math.ChunkSectionPosHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.SkyLightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SkyLightStorage.class)
public abstract class MixinSkyLightStorage implements ExtendedSkyLightStorage {
    @Shadow
    protected abstract boolean func_215551_l(long l);

    @Shadow
    protected abstract boolean func_215550_a(int blockY);

    @Override
    public boolean bridge$func_215551_l(long l) {
        return this.func_215551_l(l);
    }

    @Override
    public boolean bridge$func_215550_a(int blockY) {
        return this.func_215550_a(blockY);
    }

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

        SkyLightStorage.StorageMap data = ((ExtendedSectionLightStorage<SkyLightStorage.StorageMap>) this).bridge$getStorageUncached();

        int h = ((ExtendedSkyLightStorageMap) (Object) data).bridge$heightMap().get(SectionPos.toSectionColumnPos(chunk));

        if (h != ((ExtendedSkyLightStorageMap) (Object) data).bridge$defaultHeight() && chunkY < h) {
            NibbleArray array = ((ExtendedSectionLightStorage<SkyLightStorage.StorageMap>) this).bridge$getDataForChunk(data, chunk);

            if (array == null) {
                posY &= -16;

                while (array == null) {
                    ++chunkY;

                    if (chunkY >= h) {
                        return 15;
                    }

                    chunk = ChunkSectionPosHelper.updateYLong(chunk, chunkY);
                    posY += 16;
                    array = ((ExtendedSectionLightStorage<SkyLightStorage.StorageMap>) this).bridge$getDataForChunk(data, chunk);
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
