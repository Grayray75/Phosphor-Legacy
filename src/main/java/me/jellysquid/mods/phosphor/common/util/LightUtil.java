package me.jellysquid.mods.phosphor.common.util;

import net.minecraft.util.Direction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;

import java.util.Arrays;

public class LightUtil {
    public static final Direction[] DIRECTIONS = Direction.values();

    public static final VoxelShape[] EMPTY_LIGHT_SHAPES = new VoxelShape[DIRECTIONS.length];
    public static final VoxelShape[] NULL_LIGHT_SHAPES = new VoxelShape[DIRECTIONS.length];


    static {
        Arrays.fill(EMPTY_LIGHT_SHAPES, VoxelShapes.empty());
    }
}
