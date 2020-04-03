package me.jellysquid.mods.phosphor.mixin.block;

import me.jellysquid.mods.phosphor.common.chunk.BlockStateCacheAccess;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockState.Cache.class)
public class MixinBlockStateCache implements BlockStateCacheAccess {
    @Shadow
    @Final
    private VoxelShape[] renderShapes;

    @Shadow
    @Final
    private int opacity;

    @Override
    public VoxelShape[] getExtrudedFaces() {
        return this.renderShapes;
    }

    @Override
    public int getLightSubtracted() {
        return this.opacity;
    }
}
