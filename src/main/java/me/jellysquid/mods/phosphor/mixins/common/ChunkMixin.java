package me.jellysquid.mods.phosphor.mixins.common;

import me.jellysquid.mods.phosphor.api.IChunkLighting;
import me.jellysquid.mods.phosphor.api.IChunkLightingData;
import me.jellysquid.mods.phosphor.api.ILightingEngine;
import me.jellysquid.mods.phosphor.api.ILightingEngineProvider;
import me.jellysquid.mods.phosphor.mod.PhosphorMod;
import me.jellysquid.mods.phosphor.mod.world.WorldChunkSlice;
import me.jellysquid.mods.phosphor.mod.world.lighting.LightingHooks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Chunk.class)
public abstract class ChunkMixin implements IChunkLighting, IChunkLightingData, ILightingEngineProvider {
    private static final Direction[] HORIZONTAL = Direction.DirectionType.HORIZONTAL.getDirections();

    @Shadow
    @Final
    private ChunkSection[] chunkSections;

    @Shadow
    private boolean modified;

    @Shadow
    @Final
    private int[] heightmap;

    @Shadow
    private int minimumHeightmap;

    @Shadow
    @Final
    private World world;

    @Shadow
    private boolean terrainPopulated;

    @Final
    @Shadow
    private boolean[] columnSkyLightOutdated;

    @Final
    @Shadow
    public int chunkX;

    @Final
    @Shadow
    public int chunkZ;

    @Shadow
    private boolean isSkyLightOutdated;

    @Shadow
    protected abstract int getBlockOpacity(int x, int y, int z);

    @Shadow
    public abstract boolean hasDirectSunlight(BlockPos pos);

    /**
     * Callback injected into the Chunk ctor to cache a reference to the lighting engine from the world.
     *
     * @author JellySquid
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(CallbackInfo ci) {
        this.lightingEngine = ((ILightingEngineProvider) this.world).getLightingEngine();
    }

    /**
     * Callback injected to the head of getLightSubtracted(BlockPos, int) to force deferred light updates to be processed.
     *
     * @author JellySquid
     */
    @Inject(method = "getLightLevel", at = @At("HEAD"))
    private void onGetLightSubtracted(BlockPos pos, int amount, CallbackInfoReturnable<Integer> cir) {
        this.lightingEngine.processLightUpdates();
    }

    /**
     * Callback injected at the end of onLoad() to have previously scheduled light updates scheduled again.
     *
     * @author JellySquid
     */
    @Inject(method = "loadToWorld", at = @At("RETURN"))
    private void onLoad(CallbackInfo ci) {
        LightingHooks.scheduleRelightChecksForChunkBoundaries(this.world, (Chunk) (Object) this);
    }

    // === REPLACEMENTS ===

    /**
     * Replaces the call in setLightFor(Chunk, EnumSkyBlock, BlockPos) with our hook.
     *
     * @author JellySquid
     */
    @Redirect(
            method = "setLightAtPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;calculateSkyLight()V"
            ),
            expect = 0
    )
    private void setLightForRedirectGenerateSkylightMap(Chunk chunk, LightType lightType, BlockPos pos, int value) {
        LightingHooks.initSkylightForSection(this.world, (Chunk) (Object) this, this.chunkSections[pos.getY() >> 4]);
    }

    /**
     * @reason Overwrites relightBlock with a more efficient implementation.
     * @author JellySquid
     */
    @Overwrite
    private void lightBlock(int x, int y, int z) {
        int i = this.heightmap[z << 4 | x] & 255;
        int j = i;

        if (y > i) {
            j = y;
        }

        while (j > 0 && this.getBlockOpacity(x, j - 1, z) == 0) {
            --j;
        }

        if (j != i) {
            this.heightmap[z << 4 | x] = j;

            if (!this.world.dimension.hasNoSkylight()) {
                LightingHooks.relightSkylightColumn(this.world, (Chunk) (Object) this, x, z, i, j);
            }

            int l1 = this.heightmap[z << 4 | x];

            if (l1 < this.minimumHeightmap) {
                this.minimumHeightmap = l1;
            }
        }
    }

    /**
     * @reason Hook for calculating light updates only as needed. {@link ChunkMixin#getCachedLightFor(LightType, BlockPos)} does not
     * call this hook.
     * @author JellySquid
     */
    @Overwrite
    public int getLightAtPos(LightType lightType, BlockPos pos) {
        this.lightingEngine.processLightUpdatesForType(lightType);

        return this.getCachedLightFor(lightType, pos);
    }

    /**
     * @reason Hooks into checkLight() to check chunk lighting and returns immediately after, voiding the rest of the function.
     * @author JellySquid
     */
    @Overwrite
    public void populate() {
        this.terrainPopulated = true;

        LightingHooks.checkChunkLighting((Chunk) (Object) this, this.world);
    }

    /**
     * @reason Optimized version of recheckGaps. Avoids chunk fetches as much as possible.
     * @author JellySquid
     */
    @Overwrite
    private void recheckSkyLightGaps(boolean onlyOne) {
        this.world.profiler.push("recheckGaps");

        WorldChunkSlice slice = new WorldChunkSlice(this.world, this.chunkX, this.chunkZ);

        if (this.world.isRegionLoaded(new BlockPos(this.chunkX * 16 + 8, 0, this.chunkZ * 16 + 8), 16)) {
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    if (this.recheckGapsForColumn(slice, x, z)) {
                        if (onlyOne) {
                            this.world.profiler.pop();

                            return;
                        }
                    }
                }
            }

            this.isSkyLightOutdated = false;
        }

        this.world.profiler.pop();
    }

    private boolean recheckGapsForColumn(WorldChunkSlice slice, int x, int z) {
        int i = x + z * 16;

        if (this.columnSkyLightOutdated[i]) {
            this.columnSkyLightOutdated[i] = false;

            int height = this.getHighestBlockY(x, z);

            int x1 = this.chunkX * 16 + x;
            int z1 = this.chunkZ * 16 + z;

            int max = this.recheckGapsGetLowestHeight(slice, x1, z1);

            this.recheckGapsSkylightNeighborHeight(slice, x1, z1, height, max);

            return true;
        }

        return false;
    }

    private int recheckGapsGetLowestHeight(WorldChunkSlice slice, int x, int z) {
        int max = Integer.MAX_VALUE;

        for (Direction facing : HORIZONTAL) {
            int j = x + facing.getOffsetX();
            int k = z + facing.getOffsetZ();

            Chunk chunk = slice.getChunkFromWorldCoords(j, k);
            if (chunk != null) {
                max = Math.min(max, chunk.getMinimumHeightMap());
            }
        }

        return max;
    }

    private void recheckGapsSkylightNeighborHeight(WorldChunkSlice slice, int x, int z, int height, int max) {
        this.checkSkylightNeighborHeight(slice, x, z, max);

        for (Direction facing : HORIZONTAL) {
            int j = x + facing.getOffsetX();
            int k = z + facing.getOffsetZ();

            this.checkSkylightNeighborHeight(slice, j, k, height);
        }
    }

    private void checkSkylightNeighborHeight(WorldChunkSlice slice, int x, int z, int maxValue) {
        if (!slice.isLoaded(x, z, 16)) {
            return;
        }

        Chunk chunk = slice.getChunkFromWorldCoords(x, z);
        if (chunk == null) {
            PhosphorMod.LOGGER.warn("Chunk is null! x: " + x + " z: " + z + " maxValue: " + maxValue);
            return;
        }

        int i = chunk.getHighestBlockY(x & 15, z & 15);

        if (i > maxValue) {
            this.updateSkylightNeighborHeight(slice, x, z, maxValue, i + 1);
        }
        else if (i < maxValue) {
            this.updateSkylightNeighborHeight(slice, x, z, i, maxValue + 1);
        }
    }

    private void updateSkylightNeighborHeight(WorldChunkSlice slice, int x, int z, int startY, int endY) {
        if (endY > startY) {
            for (int i = startY; i < endY; ++i) {
                this.world.calculateLightAtPos(LightType.SKY, new BlockPos(x, i, z));
            }

            this.modified = true;
        }
    }

    @Shadow
    public abstract int getHighestBlockY(int x, int y);

    // === INTERFACE IMPL ===

    private short[] neighborLightChecks;

    private boolean isLightInitialized;

    private ILightingEngine lightingEngine;

    @Override
    public short[] getNeighborLightChecks() {
        return this.neighborLightChecks;
    }

    @Override
    public void setNeighborLightChecks(short[] data) {
        this.neighborLightChecks = data;
    }

    @Override
    public ILightingEngine getLightingEngine() {
        return this.lightingEngine;
    }

    @Override
    public boolean isLightInitialized() {
        return this.isLightInitialized;
    }

    @Override
    public void setLightInitialized(boolean lightInitialized) {
        this.isLightInitialized = lightInitialized;
    }

    @Shadow
    protected abstract void method_9167();

    @Override
    public void setSkylightUpdatedPublic() {
        this.method_9167();
    }

    @Override
    public int getCachedLightFor(LightType lightType, BlockPos pos) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        int k = pos.getZ() & 15;

        ChunkSection section = this.chunkSections[j >> 4];

        if (section == Chunk.EMPTY) {
            if (this.hasDirectSunlight(pos)) {
                return lightType.defaultValue;
            }
            else {
                return 0;
            }
        }
        else if (lightType == LightType.SKY) {
            if (this.world.dimension.hasNoSkylight()) {
                return 0;
            }
            else {
                return section.getSkyLight(i, j & 15, k);
            }
        }
        else {
            if (lightType == LightType.BLOCK) {
                return section.getBlockLight(i, j & 15, k);
            }
            else {
                return lightType.defaultValue;
            }
        }
    }

    // === END OF INTERFACE IMPL ===
}
