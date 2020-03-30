package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedLightEngine;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.lighting.LevelBasedGraph;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Arrays;
import java.util.BitSet;

@Mixin(LevelBasedGraph.class)
public abstract class MixinLevelBasedGraph implements ExtendedLightEngine {
    private static long GLOBAL_TO_CHUNK_MASK = ~BlockPos.pack(0xF, 0xF, 0xF);

    @Shadow
    @Final
    private Long2ByteMap propagationLevels;

    @Shadow
    protected abstract int getLevel(long id);

    @Shadow
    @Final
    private int levelCount;

    @Shadow
    protected abstract void propagateLevel(long sourceId, long id, int level, int currentLevel, int pendingLevel, boolean decrease);

    @Shadow
    protected abstract int getEdgeLevel(long startPos, long endPos, int startLevel);

    @Shadow
    protected abstract void cancelUpdate(long positionIn);

    private final Long2ObjectOpenHashMap<BitSet> pendingUpdatesByChunk = new Long2ObjectOpenHashMap<>(512, 0.25F);

    private BitSet[] lastChunkUpdateSets = new BitSet[2];
    private long[] lastChunkPos = new long[2];

    // [VanillaCopy] LevelPropagator#propagateLevel(long, long, int, boolean)
    @Override
    public void notifyNeighbors(long sourceId, BlockState sourceState, long targetId, int level, boolean decrease) {
        int pendingLevel = this.propagationLevels.get(targetId) & 0xFF;

        int propagatedLevel = this.getEdgeLevel(sourceId, sourceState, targetId, level);
        int clampedLevel = MathHelper.clamp(propagatedLevel, 0, this.levelCount - 1);

        if (decrease) {
            this.propagateLevel(sourceId, targetId, clampedLevel, this.getLevel(targetId), pendingLevel, true);

            return;
        }

        boolean flag;
        int resultLevel;

        if (pendingLevel == 0xFF) {
            flag = true;
            resultLevel = MathHelper.clamp(this.getLevel(targetId), 0, this.levelCount - 1);
        } else {
            resultLevel = pendingLevel;
            flag = false;
        }

        if (clampedLevel == resultLevel) {
            this.propagateLevel(sourceId, targetId, this.levelCount - 1, flag ? resultLevel : this.getLevel(targetId), pendingLevel, false);
        }
    }

    @Override
    public int getEdgeLevel(long sourceId, BlockState sourceState, long targetId, int level) {
        return this.getEdgeLevel(sourceId, targetId, level);
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

            this.cancelUpdate(BlockPos.pack(startX + x, startY + y, startZ + z));
        });
    }

    @Redirect(method = "removeToUpdate", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ByteMap;remove(J)B", remap = false))
    private byte redirectRemoveToUpdate(Long2ByteMap map, long key) {
        this.onPendingUpdateRemoved(key);
        return map.remove(key);
    }

    @Redirect(method = "addToUpdate", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ByteMap;put(JB)B", remap = false))
    private byte redirectAddToUpdate(Long2ByteMap map, long key, byte value) {
        this.onPendingUpdateAdded(key);
        return map.put(key, value);
    }

    @Redirect(method = "processUpdates", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ByteMap;remove(J)B", remap = false))
    private byte redirectProcessUpdates(Long2ByteMap map, long key) {
        this.onPendingUpdateRemoved(key);
        return map.remove(key);
    }

    private void onPendingUpdateRemoved(long blockPos) {
        BitSet set = this.getUpdateSetFor(toChunkKey(blockPos));

        if (set != null) {
            set.clear(toLocalKey(blockPos));

            if (set.isEmpty()) {
                this.pendingUpdatesByChunk.remove(toChunkKey(blockPos));
            }
        }
    }

    private void onPendingUpdateAdded(long blockPos) {
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
