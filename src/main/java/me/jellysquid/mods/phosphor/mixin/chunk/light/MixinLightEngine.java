package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.jellysquid.mods.phosphor.common.chunk.BlockStateAccess;
import me.jellysquid.mods.phosphor.common.chunk.BlockStateCacheAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.LightEngineExtended;
import me.jellysquid.mods.phosphor.common.chunk.light.PendingUpdateListener;
import me.jellysquid.mods.phosphor.common.util.cache.LightEngineBlockAccess;
import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.IChunkLightProvider;
import net.minecraft.world.lighting.LightDataMap;
import net.minecraft.world.lighting.LightEngine;
import net.minecraft.world.lighting.SectionLightStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.BitSet;

@Mixin(LightEngine.class)
public class MixinLightEngine<M extends LightDataMap<M>, S extends SectionLightStorage<M>> implements LightEngineExtended, PendingUpdateListener {
    private static long GLOBAL_TO_CHUNK_MASK = ~BlockPos.pack(0xF, 0xF, 0xF);

    @Shadow
    @Final
    protected BlockPos.Mutable scratchPos;

    @Shadow
    @Final
    protected IChunkLightProvider chunkProvider;

    private LightEngineBlockAccess blockAccess;

    private final Long2ObjectOpenHashMap<BitSet> pendingUpdatesByChunk = new Long2ObjectOpenHashMap<>(512, 0.25F);

    private BitSet[] lastChunkUpdateSets = new BitSet[2];
    private long[] lastChunkPos = new long[2];

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(IChunkLightProvider lightProvider, LightType lightType, S storage, CallbackInfo ci) {
        this.blockAccess = new LightEngineBlockAccess(lightProvider);
    }

    @Inject(method = "invalidateCaches", at = @At("RETURN"))
    private void onCleanup(CallbackInfo ci) {
        // This callback may be executed from the constructor above, and the object won't be initialized then
        if (this.blockAccess != null) {
            this.blockAccess.reset();
        }
    }

    // [VanillaCopy] method_20479
    @Override
    public BlockState getBlockStateForLighting(int x, int y, int z) {
        return this.blockAccess.getBlockState(x, y, z);
    }

    // [VanillaCopy] method_20479
    @Override
    public int getSubtractedLight(BlockState state, int x, int y, int z) {
        BlockStateCacheAccess shapeCache = ((BlockStateAccess) state).getCache();

        if (shapeCache != null) {
            return shapeCache.getLightSubtracted();
        } else {
            return state.getBlock().getOpacity(state, this.chunkProvider.getWorld(), this.scratchPos.setPos(x, y, z));
        }
    }

    // [VanillaCopy] method_20479
    @Override
    public VoxelShape getOpaqueShape(BlockState state, int x, int y, int z, Direction dir) {
        if (state == null || !state.isTransparent()) {
            return VoxelShapes.empty();
        }

        BlockStateCacheAccess cache = ((BlockStateAccess) state).getCache();

        if (cache != null) {
            VoxelShape[] extrudedFaces = cache.getExtrudedFaces();

            if (extrudedFaces != null) {
                return extrudedFaces[dir.ordinal()];
            }

            return VoxelShapes.empty();
        } else {
            return VoxelShapes.getFaceShape(state.getRenderShape(this.chunkProvider.getWorld(), this.scratchPos.setPos(x, y, z)), dir);
        }
    }
    /**
     * The vanilla implementation for removing pending light updates requires iterating over either every queued light
     * update (<8K checks) or every block position within a sub-chunk (16^3 checks). This is painfully slow and results
     * in a tremendous amount of CPU time being spent here when chunks are unloaded on the client and server.
     *
     * To work around this, we maintain a list of queued updates by chunk position so we can simply select every light
     * update within a chunk and drop them in one operation.
     */
    @Override
    public void cancelUpdatesForChunk(long chunkPos) {
        int chunkX = SectionPos.extractX(chunkPos);
        int chunkY = SectionPos.extractY(chunkPos);
        int chunkZ = SectionPos.extractZ(chunkPos);

        long key = toChunkKey(BlockPos.pack(chunkX << 4, chunkY << 4, chunkZ << 4));

        BitSet set = this.pendingUpdatesByChunk.remove(key);

        if (set == null || set.isEmpty()) {
            return;
        }

        this.resetUpdateSetCache();

        int startX = chunkX << 4;
        int startY = chunkY << 4;
        int startZ = chunkZ << 4;

        set.stream().forEach(i -> {
            int x = (i >> 8) & 0xF;
            int y = (i >> 4) & 0xF;
            int z = i & 0xF;

            this.cancelUpdatesForChunk(BlockPos.pack(startX + x, startY + y, startZ + z));
        });
    }

    @Override
    public void onPendingUpdateRemoved(long blockPos) {
        BitSet set = this.getUpdateSetFor(toChunkKey(blockPos));

        if (set != null) {
            set.clear(toLocalKey(blockPos));

            if (set.isEmpty()) {
                this.pendingUpdatesByChunk.remove(toChunkKey(blockPos));
            }
        }
    }

    @Override
    public void onPendingUpdateAdded(long blockPos) {
        BitSet set = this.getOrCreateUpdateSetFor(toChunkKey(blockPos));
        set.set(toLocalKey(blockPos));
    }

    private BitSet getUpdateSetFor(long chunkPos) {
        BitSet set = this.getCachedUpdateSet(chunkPos);

        if (set == null) {
            set = this.pendingUpdatesByChunk.get(chunkPos);

            if (set != null) {
                this.addUpdateSetToCache(chunkPos, set);
            }
        }

        return set;
    }

    private BitSet getOrCreateUpdateSetFor(long chunkPos) {
        BitSet set = this.getCachedUpdateSet(chunkPos);

        if (set == null) {
            set = this.pendingUpdatesByChunk.get(chunkPos);

            if (set == null) {
                this.pendingUpdatesByChunk.put(chunkPos, set = new BitSet(4096));
                this.addUpdateSetToCache(chunkPos, set);
            }
        }

        return set;
    }

    private BitSet getCachedUpdateSet(long chunkPos) {
        long[] lastChunkPos = this.lastChunkPos;

        for (int i = 0; i < lastChunkPos.length; i++) {
            if (lastChunkPos[i] == chunkPos) {
                return this.lastChunkUpdateSets[i];
            }
        }

        return null;
    }

    private void addUpdateSetToCache(long chunkPos, BitSet set) {
        long[] lastPos = this.lastChunkPos;
        lastPos[1] = lastPos[0];
        lastPos[0] = chunkPos;

        BitSet[] lastSet = this.lastChunkUpdateSets;
        lastSet[1] = lastSet[0];
        lastSet[0] = set;
    }

    protected void resetUpdateSetCache() {
        Arrays.fill(this.lastChunkPos, Long.MIN_VALUE);
        Arrays.fill(this.lastChunkUpdateSets, null);
    }

    private static long toChunkKey(long blockPos) {
        return blockPos & GLOBAL_TO_CHUNK_MASK;
    }

    private static int toLocalKey(long pos) {
        int x = BlockPos.unpackX(pos) & 0xF;
        int y = BlockPos.unpackY(pos) & 0xF;
        int z = BlockPos.unpackZ(pos) & 0xF;

        return x << 8 | y << 4 | z;
    }
}
