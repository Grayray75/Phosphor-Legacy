package me.jellysquid.mods.phosphor.mixins.common;

import me.jellysquid.mods.phosphor.mod.world.lighting.LightingHooks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(WorldChunk.class)
public abstract class ChunkMixin$Vanilla {
    private static final String SET_BLOCK_STATE_VANILLA = "Lnet/minecraft/world/chunk/WorldChunk;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/BlockState;)Lnet/minecraft/block/state/BlockState;";

    @Shadow
    @Final
    private World world;

    /**
     * Redirects the construction of the ExtendedBlockStorage in setBlockState(BlockPos, IBlockState). We need to initialize
     * the skylight data for the constructed section as soon as possible.
     *
     * @author JellySquid
     */
    @Redirect(
            method = SET_BLOCK_STATE_VANILLA,
            at = @At(
                    value = "NEW",
                    args = "class=net/minecraft/world/chunk/WorldChunkSection"
            ),
            expect = 0
    )
    private WorldChunkSection setBlockStateCreateSectionVanilla(int y, boolean storeSkylight) {
        return this.initSection(y, storeSkylight);
    }

    private WorldChunkSection initSection(int y, boolean storeSkylight) {
        WorldChunkSection storage = new WorldChunkSection(y, storeSkylight);

        LightingHooks.initSkylightForSection(this.world, (WorldChunk) (Object) this, storage);

        return storage;
    }

    /**
     * Modifies the flag variable of setBlockState(BlockPos, IBlockState) to always be false after it is set.
     *
     * @author JellySquid
     */
    @ModifyVariable(
            method = SET_BLOCK_STATE_VANILLA,
            at = @At(
                    value = "STORE",
                    ordinal = 1
            ),
            index = 12,
            name = "bl",
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/world/chunk/WorldChunk;chunkSections:[Lnet/minecraft/world/chunk/WorldChunkSection;"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/WorldChunkSection;setBlockState(IIILnet/minecraft/block/state/BlockState;)V"
                    )

            ),
            allow = 1
    )
    private boolean setBlockStateInjectGenerateSkylightMapVanilla(boolean generateSkylight) {
        return false;
    }

    /**
     * Modifies variable k1 before the conditional which decides to propagate skylight as to prevent it from
     * ever evaluating as true
     *
     * @author JellySquid
     */
    @ModifyVariable(
            method = SET_BLOCK_STATE_VANILLA,
            at = @At(
                    value = "LOAD",
                    ordinal = 0
            ),
            index = 13,
            name = "o",
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/WorldChunk;resetLightAt(III)V",
                            ordinal = 1
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/WorldChunk;queueLightUpdate(II)V"
                    )

            ),
            allow = 1
    )
    private int setBlockStatePreventPropagateSkylightOcclusion1(int generateSkylight) {
        return WIZARD_MAGIC;
    }

    /**
     * Modifies variable j1 before the conditional which decides to propagate skylight as to prevent it from
     * ever evaluating as true.
     *
     * @author JellySquid
     */
    @ModifyVariable(
            method = SET_BLOCK_STATE_VANILLA,
            at = @At(
                    value = "LOAD",
                    ordinal = 1
            ),
            index = 14,
            name = "j1",
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/WorldChunk;resetLightAt(III)V",
                            ordinal = 1
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/WorldChunk;queueLightUpdate(II)V"
                    )

            ),
            allow = 1
    )
    private int setBlockStatePreventPropagateSkylightOcclusion2(int generateSkylight) {
        return WIZARD_MAGIC;
    }

    private static final int WIZARD_MAGIC = 694698818;
}
