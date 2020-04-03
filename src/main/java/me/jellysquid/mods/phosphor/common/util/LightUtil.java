package me.jellysquid.mods.phosphor.common.util;

import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;

public class LightUtil {
    /**
     * Replacement for {@link VoxelShapes#faceShapeCovers(VoxelShape, VoxelShape)}. This implementation early-exits
     * in some common situations to avoid unnecessary computation.
     *
     * @author JellySquid
     */
    public static boolean faceShapeCovers(VoxelShape a, VoxelShape b) {
        // At least one shape is a full cube and will match
        if (a == VoxelShapes.fullCube() || b == VoxelShapes.fullCube()) {
            return true;
        }

        boolean ae = a == VoxelShapes.empty() || a.isEmpty();
        boolean be = b == VoxelShapes.empty() || b.isEmpty();

        // If both shapes are empty, they can never overlap
        if (ae && be) {
            return false;
        }

        // Test each shape individually if they're non-empty and fail fast
        return (ae || !VoxelShapes.compare(VoxelShapes.fullCube(), a, IBooleanFunction.ONLY_FIRST)) &&
                (be || !VoxelShapes.compare(VoxelShapes.fullCube(), b, IBooleanFunction.ONLY_FIRST));
    }
}
