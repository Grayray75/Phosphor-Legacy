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
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class ChunkMixin implements IChunkLighting, IChunkLightingData, ILightingEngineProvider {
    private static final Direction[] HORIZONTAL = Direction.Plane.HORIZONTAL.get();

    @Shadow
    @Final
    private WorldChunkSection[] sections;

    @Shadow
    private boolean dirty;

    @Shadow
    @Final
    private int[] heightMap;

    @Shadow
    private int lowestHeight;

    @Shadow
    @Final
    private World world;

    @Shadow
    private boolean terrainPopulated;

    @Final
    @Shadow
    private boolean[] needsLightUpdate;

    @Final
    @Shadow
    public int chunkX;

    @Final
    @Shadow
    public int chunkZ;

    @Shadow
    private boolean lightPopulated;

    @Shadow
    protected abstract int getOpacity(int x, int y, int z);

    @Shadow
    public abstract boolean hasSkyAccess(BlockPos pos);

    /**
     * Callback injected into the WorldChunk ctor to cache a reference to the lighting engine from the world.
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
    @Inject(method = "getLight", at = @At("HEAD"))
    private void onGetLightSubtracted(BlockPos pos, int amount, CallbackInfoReturnable<Integer> cir) {
        this.lightingEngine.processLightUpdates();
    }

    /**
     * Callback injected at the end of onLoad() to have previously scheduled light updates scheduled again.
     *
     * @author JellySquid
     */
    @Inject(method = "load", at = @At("RETURN"))
    private void onLoad(CallbackInfo ci) {
        LightingHooks.scheduleRelightChecksForChunkBoundaries(this.world, (WorldChunk) (Object) this);
    }

    // === REPLACEMENTS ===

    /**
     * Replaces the call in setLightFor(WorldChunk, EnumSkyBlock, BlockPos) with our hook.
     *
     * @author JellySquid
     */
    @Redirect(
            method = "setLight",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/WorldChunk;populateSkylight()V"
            ),
            expect = 0
    )
    private void setLightForRedirectGenerateSkylightMap(WorldChunk chunk, LightType lightType, BlockPos pos, int value) {
        LightingHooks.initSkylightForSection(this.world, (WorldChunk) (Object) this, this.sections[pos.getY() >> 4]);
    }

    /**
     * @reason Overwrites relightBlock with a more efficient implementation.
     * @author JellySquid
     */
    @Overwrite
    private void resetLightAt(int x, int y, int z) {
        int i = this.heightMap[z << 4 | x] & 255;
        int j = i;

        if (y > i) {
            j = y;
        }

        while (j > 0 && this.getOpacity(x, j - 1, z) == 0) {
            --j;
        }

        if (j != i) {
            this.heightMap[z << 4 | x] = j;

            if (!this.world.dimension.isDark()) {
                LightingHooks.relightSkylightColumn(this.world, (WorldChunk) (Object) this, x, z, i, j);
            }

            int l1 = this.heightMap[z << 4 | x];

            if (l1 < this.lowestHeight) {
                this.lowestHeight = l1;
            }
        }
    }

    /**
     * @reason Hook for calculating light updates only as needed. {@link ChunkMixin#getCachedLightFor(LightType, BlockPos)} does not
     * call this hook.
     * @author JellySquid
     */
    @Overwrite
    public int getLight(LightType lightType, BlockPos pos) {
        this.lightingEngine.processLightUpdatesForType(lightType);

        return this.getCachedLightFor(lightType, pos);
    }

    /**
     * @reason Hooks into checkLight() to check chunk lighting and returns immediately after, voiding the rest of the function.
     * @author JellySquid
     */
    @Overwrite
    public void populateLight() {
        this.terrainPopulated = true;

        LightingHooks.checkChunkLighting((WorldChunk) (Object) this, this.world);
    }

    /**
     * @reason Optimized version of recheckGaps. Avoids chunk fetches as much as possible.
     * @author JellySquid
     */
    @Overwrite
    private void recheckGaps(boolean onlyOne) {
        this.world.profiler.push("recheckGaps");

        WorldChunkSlice slice = new WorldChunkSlice(this.world, this.chunkX, this.chunkZ);

        if (this.world.isAreaLoaded(new BlockPos(this.chunkX * 16 + 8, 0, this.chunkZ * 16 + 8), 16)) {
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

            this.lightPopulated = false;
        }

        this.world.profiler.pop();
    }

    private boolean recheckGapsForColumn(WorldChunkSlice slice, int x, int z) {
        int i = x + z * 16;

        if (this.needsLightUpdate[i]) {
            this.needsLightUpdate[i] = false;

            int height = this.getHeight(x, z);

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

            WorldChunk chunk = slice.getChunkFromWorldCoords(j, k);
            if (chunk != null) {
                max = Math.min(max, chunk.getLowestHeight());
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

        WorldChunk chunk = slice.getChunkFromWorldCoords(x, z);
        if (chunk == null) {
            PhosphorMod.LOGGER.warn("WorldChunk is null! x: " + x + " z: " + z + " maxValue: " + maxValue);
            return;
        }

        int i = chunk.getHeight(x & 15, z & 15);

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
                this.world.checkLight(LightType.SKY, new BlockPos(x, i, z));
            }

            this.dirty = true;
        }
    }

    @Shadow
    public abstract int getHeight(int x, int y);

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
    protected abstract void populateSkylight();

    @Override
    public void setSkylightUpdatedPublic() {
        this.populateSkylight();
    }

    @Override
    public int getCachedLightFor(LightType lightType, BlockPos pos) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        int k = pos.getZ() & 15;

        WorldChunkSection section = this.sections[j >> 4];

        if (section == WorldChunk.EMPTY) {
            if (this.hasSkyAccess(pos)) {
                return lightType.defaultValue;
            }
            else {
                return 0;
            }
        }
        else if (lightType == LightType.SKY) {
            if (this.world.dimension.isDark()) {
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
