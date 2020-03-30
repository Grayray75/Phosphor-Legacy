package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.LongSet;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedLightEngine;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedSectionLightStorage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.LightDataMap;
import net.minecraft.world.lighting.LightEngine;
import net.minecraft.world.lighting.SectionLightStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionLightStorage.class)
public abstract class MixinSectionLightStorage<M extends LightDataMap<M>> implements ExtendedSectionLightStorage<M> {
    @Shadow
    @Final
    protected M cachedLightData;

    @Mutable
    @Shadow
    @Final
    protected LongSet dirtyCachedSections;

    @Shadow
    protected abstract NibbleArray getArray(long chunkPos, boolean cached);

    @Mutable
    @Shadow
    @Final
    protected LongSet changedLightPositions;

    @Shadow
    protected abstract int getLevel(long id);

    @Mutable
    @Shadow
    @Final
    protected LongSet activeLightSections;

    @Mutable
    @Shadow
    @Final
    protected LongSet addedActiveLightSections;

    @Mutable
    @Shadow
    @Final
    protected LongSet addedEmptySections;

    @Mutable
    @Shadow
    @Final
    private LongSet noLightSections;

    @Shadow
    protected abstract void func_215524_j(long blockPos);

    @SuppressWarnings("unused")
    @Shadow
    protected volatile boolean hasSectionsToUpdate;

    @Shadow
    protected abstract NibbleArray getOrCreateArray(long pos);

    @Shadow
    protected volatile M uncachedLightData;

    @Shadow
    protected abstract boolean hasSection(long p_215518_1_);

    /**
     * Replaces the two set of calls to unpack the XYZ coordinates from the input to just one, storing the result as local
     * variables.
     *
     * @reason Use faster implementation
     * @author JellySquid
     */
    @Overwrite
    public int getLight(long blockPos) {
        int x = BlockPos.unpackX(blockPos);
        int y = BlockPos.unpackY(blockPos);
        int z = BlockPos.unpackZ(blockPos);

        long chunk = SectionPos.asLong(SectionPos.toChunk(x), SectionPos.toChunk(y), SectionPos.toChunk(z));

        NibbleArray array = this.getArray(chunk, true);

        return array.get(SectionPos.mask(x), SectionPos.mask(y), SectionPos.mask(z));
    }

    /**
     * An extremely important optimization is made here in regards to adding items to the pending notification set. The
     * original implementation attempts to add the coordinate of every chunk which contains a neighboring block position
     * even though a huge number of loop iterations will simply map to block positions within the same updating chunk.
     * <p>
     * Our implementation here avoids this by pre-calculating the min/max chunk coordinates so we can iterate over only
     * the relevant chunk positions once. This reduces what would always be 27 iterations to just 1-8 iterations.
     *
     * @reason Use faster implementation
     * @author JellySquid
     */
    @Overwrite
    public void setLight(long blockPos, int value) {
        int x = BlockPos.unpackX(blockPos);
        int y = BlockPos.unpackY(blockPos);
        int z = BlockPos.unpackZ(blockPos);

        long chunkPos = SectionPos.asLong(x >> 4, y >> 4, z >> 4);

        if (this.dirtyCachedSections.add(chunkPos)) {
            this.cachedLightData.copyArray(chunkPos);
        }

        NibbleArray nibble = this.getArray(chunkPos, true);
        nibble.set(x & 15, y & 15, z & 15, value);

        for (int z2 = (z - 1) >> 4; z2 <= (z + 1) >> 4; ++z2) {
            for (int x2 = (x - 1) >> 4; x2 <= (x + 1) >> 4; ++x2) {
                for (int y2 = (y - 1) >> 4; y2 <= (y + 1) >> 4; ++y2) {
                    this.changedLightPositions.add(SectionPos.asLong(x2, y2, z2));
                }
            }
        }
    }

    /**
     * Combines the contains/remove call to the queued removals set into a single remove call. See {@link MixinSectionLightStorage#setLight(long, int)}
     * for additional information.
     *
     * @reason Use faster implementation
     * @author JellySquid
     */
    @Overwrite
    public void setLevel(long id, int level) {
        int prevLevel = this.getLevel(id);

        if (prevLevel != 0 && level == 0) {
            this.activeLightSections.add(id);
            this.addedActiveLightSections.remove(id);
        }

        if (prevLevel == 0 && level != 0) {
            this.activeLightSections.remove(id);
            this.addedEmptySections.remove(id);
        }

        if (prevLevel >= 2 && level != 2) {
            if (!this.noLightSections.remove(id)) {
                this.cachedLightData.setArray(id, this.getOrCreateArray(id));

                this.dirtyCachedSections.add(id);
                this.func_215524_j(id);

                int x = BlockPos.unpackX(id);
                int y = BlockPos.unpackY(id);
                int z = BlockPos.unpackZ(id);

                for (int z2 = (z - 1) >> 4; z2 <= (z + 1) >> 4; ++z2) {
                    for (int x2 = (x - 1) >> 4; x2 <= (x + 1) >> 4; ++x2) {
                        for (int y2 = (y - 1) >> 4; y2 <= (y + 1) >> 4; ++y2) {
                            this.changedLightPositions.add(SectionPos.asLong(x2, y2, z2));
                        }
                    }
                }
            }
        }

        if (prevLevel != 2 && level >= 2) {
            this.noLightSections.add(id);
        }

        this.hasSectionsToUpdate = !this.noLightSections.isEmpty();
    }

    /**
     * @reason Drastically improve efficiency by making removals O(n) instead of O(16*16*16)
     * @author JellySquid
     */
    @Inject(method = "cancelSectionUpdates", at = @At("HEAD"), cancellable = true)
    protected void removeChunkData(LightEngine<?, ?> engine, long pos, CallbackInfo ci) {
        if (engine instanceof ExtendedLightEngine) {
            ((ExtendedLightEngine) engine).cancelUpdatesForChunk(pos);

            ci.cancel();
        }
    }

    @Override
    public boolean bridge$hasChunk(long chunkPos) {
        return this.hasSection(chunkPos);
    }

    @Override
    public NibbleArray bridge$getDataForChunk(M data, long chunkPos) {
        return data.getArray(chunkPos);
    }

    @Override
    public M bridge$getStorageUncached() {
        return this.uncachedLightData;
    }
}
