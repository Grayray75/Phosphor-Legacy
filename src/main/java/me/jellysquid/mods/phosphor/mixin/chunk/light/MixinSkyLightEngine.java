package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.ExtendedChunkLightProvider;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedGenericLightStorage;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedLightEngine;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedSkyLightStorage;
import me.jellysquid.mods.phosphor.common.util.math.ChunkSectionPosHelper;
import me.jellysquid.mods.phosphor.common.util.math.DirectionHelper;
import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.IChunkLightProvider;
import net.minecraft.world.lighting.LightEngine;
import net.minecraft.world.lighting.SkyLightEngine;
import net.minecraft.world.lighting.SkyLightStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import static net.minecraft.util.math.SectionPos.mask;
import static net.minecraft.util.math.SectionPos.toChunk;

@Mixin(SkyLightEngine.class)
public abstract class MixinSkyLightEngine extends LightEngine<SkyLightStorage.StorageMap, SkyLightStorage>
        implements ExtendedLightEngine, ExtendedChunkLightProvider {
    @Shadow
    @Final
    private static Direction[] CARDINALS;

    @Shadow
    @Final
    private static Direction[] DIRECTIONS;

    public MixinSkyLightEngine(IChunkLightProvider lightProvider, LightType lightType, SkyLightStorage storage) {
        super(lightProvider, lightType, storage);
    }

    /**
     * @author JellySquid
     * @reason Use optimized method below
     */
    @Override
    @Overwrite
    public int getEdgeLevel(long fromId, long toId, int currentLevel) {
        return this.getEdgeLevel(fromId, null, toId, currentLevel);
    }

    /**
     * This breaks up the call to method_20479 into smaller parts so we do not have to pass a mutable heap object
     * to the method in order to extract the light result. This has a few other advantages, allowing us to:
     * - Avoid the de-optimization that occurs from allocating and passing a heap object
     * - Avoid unpacking coordinates twice for both the call to method_20479 and method_20710.
     * - Avoid the the specific usage of AtomicInteger, which has additional overhead for the atomic get/set operations.
     * - Avoid checking if the checked block is opaque twice.
     * - Avoid a redundant block state lookup by re-using {@param fromState}
     *
     * The rest of the implementation has been otherwise copied from vanilla, but is optimized to avoid constantly
     * (un)packing coordinates and to use an optimized direction lookup function.
     *
     * @param fromState The re-usable block state at position {@param fromId}
     */
    @Override
    public int getEdgeLevel(long fromId, BlockState fromState, long toId, int currentLevel) {
        if (toId == Long.MAX_VALUE) {
            return 15;
        }

        if (fromId == Long.MAX_VALUE) {
            if (((ExtendedSkyLightStorage) this.storage).bridge$func_215551_l(toId)) {
                currentLevel = 0;
            } else {
                return 15;
            }
        }

        if (currentLevel >= 15) {
            return currentLevel;
        }

        int toX = BlockPos.unpackX(toId);
        int toY = BlockPos.unpackY(toId);
        int toZ = BlockPos.unpackZ(toId);

        BlockState toState = this.getBlockStateForLighting(toX, toY, toZ);

        if (toState == null) {
            return 15;
        }

        int fromX = BlockPos.unpackX(fromId);
        int fromY = BlockPos.unpackY(fromId);
        int fromZ = BlockPos.unpackZ(fromId);

        if (fromState == null) {
            fromState = this.getBlockStateForLighting(fromX, fromY, fromZ);
        }

        boolean verticalOnly = fromX == toX && fromZ == toZ;

        Direction dir;

        if (fromId == Long.MAX_VALUE) {
            dir = Direction.DOWN;
        } else {
            dir = DirectionHelper.getVecDirection(toX - fromX, toY - fromY, toZ - fromZ);
        }

        if (dir != null) {
            VoxelShape toShape = this.getOpaqueShape(toState, toX, toY, toZ, dir.getOpposite());

            if (toShape != VoxelShapes.fullCube()) {
                VoxelShape fromShape = this.getOpaqueShape(fromState, fromX, fromY, fromZ, dir);

                if (VoxelShapes.faceShapeCovers(fromShape, toShape)) {
                    return 15;
                }
            }
        } else {
            Direction altDir = Direction.byLong(toX - fromX, verticalOnly ? -1 : 0, toZ - fromZ);

            if (altDir == null) {
                return 15;
            }

            VoxelShape toShape = this.getOpaqueShape(toState, toX, toY, toZ, altDir.getOpposite());

            if (VoxelShapes.faceShapeCovers(VoxelShapes.empty(), toShape)) {
                return 15;
            }

            VoxelShape fromShape = this.getOpaqueShape(fromState, fromX, fromY, fromZ, Direction.DOWN);

            if (VoxelShapes.faceShapeCovers(fromShape, VoxelShapes.empty())) {
                return 15;
            }
        }

        int out = this.getSubtractedLight(toState, toX, toY, toZ);

        if ((fromId == Long.MAX_VALUE || verticalOnly && fromY > toY) && currentLevel == 0 && out == 0) {
            return 0;
        } else {
            return currentLevel + Math.max(1, out);
        }
    }

    /**
     * A few key optimizations are made here, in particular:
     * - The code avoids un-packing coordinates as much as possible and stores the results into local variables.
     * - When necessary, coordinate re-packing is reduced to the minimum number of operations. Most of them can be reduced
     * to only updating the Y-coordinate versus re-computing the entire integer.
     * - Coordinate re-packing is removed where unnecessary (such as when only comparing the Y-coordinate of two positions)
     * - A special propagation method is used that allows the BlockState at {@param id} to be passed, allowing the code
     * which follows to simply re-use it instead of redundantly retrieving another block state.
     *
     * This copies the vanilla implementation as close as possible.
     *
     * @reason Use faster implementation
     * @author JellySquid
     */
    @Override
    @Overwrite
    public void notifyNeighbors(long id, int targetLevel, boolean mergeAsMin) {
        long chunkId = SectionPos.worldToSection(id);

        int x = BlockPos.unpackX(id);
        int y = BlockPos.unpackY(id);
        int z = BlockPos.unpackZ(id);

        int localX = mask(x);
        int localY = mask(y);
        int localZ = mask(z);

        BlockState fromState = this.getBlockStateForLighting(x, y, z);

        // Fast-path: Use much simpler logic if we do not need to access adjacent chunks
        if (localX > 0 && localX < 15 && localY > 0 && localY < 15 && localZ > 0 && localZ < 15) {
            for (Direction dir : DIRECTIONS) {
                this.notifyNeighbors(id, fromState, BlockPos.pack(x + dir.getXOffset(), y + dir.getYOffset(), z + dir.getZOffset()), targetLevel, mergeAsMin);
            }

            return;
        }

        int chunkY = toChunk(y);
        int chunkOffsetY = 0;

        // Skylight optimization: Try to find bottom-most non-empty chunk
        if (localY == 0) {
            while (!((ExtendedGenericLightStorage) this.storage).bridge$hasChunk(SectionPos.withOffset(chunkId, 0, -chunkOffsetY - 1, 0))
                    && ((ExtendedSkyLightStorage) this.storage).bridge$func_215550_a(chunkY - chunkOffsetY - 1)) {
                ++chunkOffsetY;
            }
        }

        int belowY = y + (-1 - chunkOffsetY * 16);
        int belowChunkY = toChunk(belowY);

        if (chunkY == belowChunkY || ((ExtendedGenericLightStorage) this.storage).bridge$hasChunk(ChunkSectionPosHelper.updateYLong(chunkId, belowChunkY))) {
            this.notifyNeighbors(id, fromState, BlockPos.pack(x, belowY, z), targetLevel, mergeAsMin);
        }

        int aboveY = y + 1;
        int aboveChunkY = toChunk(aboveY);

        if (chunkY == aboveChunkY || ((ExtendedGenericLightStorage) this.storage).bridge$hasChunk(ChunkSectionPosHelper.updateYLong(chunkId, aboveChunkY))) {
            this.notifyNeighbors(id, fromState, BlockPos.pack(x, aboveY, z), targetLevel, mergeAsMin);
        }

        for (Direction dir : CARDINALS) {
            int adjX = x + dir.getXOffset();
            int adjZ = z + dir.getZOffset();

            int offsetY = 0;

            while (true) {
                int adjY = y - offsetY;

                long offsetId = BlockPos.pack(adjX, adjY, adjZ);
                long offsetChunkId = SectionPos.worldToSection(offsetId);

                boolean flag = chunkId == offsetChunkId;

                if (flag || ((ExtendedGenericLightStorage) this.storage).bridge$hasChunk(offsetChunkId)) {
                    this.notifyNeighbors(id, fromState, offsetId, targetLevel, mergeAsMin);
                }

                if (flag) {
                    break;
                }

                offsetY++;

                if (offsetY > chunkOffsetY * 16) {
                    break;
                }
            }
        }
    }

}
