package me.jellysquid.mods.phosphor.mod.world.lighting;

import me.jellysquid.mods.phosphor.api.IChunkLighting;
import me.jellysquid.mods.phosphor.api.IChunkLightingData;
import me.jellysquid.mods.phosphor.api.ILightingEngine;
import me.jellysquid.mods.phosphor.api.ILightingEngineProvider;
import me.jellysquid.mods.phosphor.mixins.common.ChunkSectionAccessor;
import me.jellysquid.mods.phosphor.mixins.common.DirectionAccessor;
import me.jellysquid.mods.phosphor.mixins.common.PaletteContainerAccessor;
import me.jellysquid.mods.phosphor.mod.PhosphorMod;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtShort;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

@SuppressWarnings("unused")
public class LightingHooks {
    private static final LightType[] ENUM_SKY_BLOCK_VALUES = LightType.values();

    private static final Direction.AxisDirection[] ENUM_AXIS_DIRECTION_VALUES = Direction.AxisDirection.values();

    private static final int FLAG_COUNT = 32; //2 light types * 4 directions * 2 halves * (inwards + outwards)

    public static void relightSkylightColumn(final World world, final Chunk chunk, final int x, final int z, final int height1, final int height2) {
        final int yMin = Math.min(height1, height2);
        final int yMax = Math.max(height1, height2) - 1;

        final ChunkSection[] sections = chunk.getBlockStorage();

        final int xBase = (chunk.chunkX << 4) + x;
        final int zBase = (chunk.chunkZ << 4) + z;

        scheduleRelightChecksForColumn(world, LightType.SKY, xBase, zBase, yMin, yMax);

        if (sections[yMin >> 4] == Chunk.EMPTY && yMin > 0) {
            world.method_8539(LightType.SKY, new BlockPos(xBase, yMin - 1, zBase));
        }

        short emptySections = 0;

        for (int sec = yMax >> 4; sec >= yMin >> 4; --sec) {
            if (sections[sec] == Chunk.EMPTY) {
                emptySections |= 1 << sec;
            }
        }

        if (emptySections != 0) {
            for (final Direction dir : DirectionAccessor.getHorizontal()) {
                final int xOffset = dir.getOffsetX();
                final int zOffset = dir.getOffsetZ();

                final boolean neighborColumnExists = (((x + xOffset) | (z + zOffset)) & 16) == 0
                        //Checks whether the position is at the specified border (the 16 bit is set for both 15+1 and 0-1)
                        || world.getChunkProvider().getLoadedChunk(chunk.chunkX + xOffset, chunk.chunkZ + zOffset) != null;

                if (neighborColumnExists) {
                    for (int sec = yMax >> 4; sec >= yMin >> 4; --sec) {
                        if ((emptySections & (1 << sec)) != 0) {
                            scheduleRelightChecksForColumn(world, LightType.SKY, xBase + xOffset, zBase + zOffset, sec << 4, (sec << 4) + 15);
                        }
                    }
                }
                else {
                    flagChunkBoundaryForUpdate(chunk, emptySections, LightType.SKY, dir, getAxisDirection(dir, x, z), EnumBoundaryFacing.OUT);
                }
            }
        }
    }

    public static void scheduleRelightChecksForArea(final World world, final LightType lightType, final int xMin, final int yMin, final int zMin, final int xMax, final int yMax, final int zMax) {
        for (int x = xMin; x <= xMax; ++x) {
            for (int z = zMin; z <= zMax; ++z) {
                scheduleRelightChecksForColumn(world, lightType, x, z, yMin, yMax);
            }
        }
    }

    private static void scheduleRelightChecksForColumn(final World world, final LightType lightType, final int x, final int z, final int yMin, final int yMax) {
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int y = yMin; y <= yMax; ++y) {
            world.method_8539(lightType, pos.setPosition(x, y, z));
        }
    }

    public enum EnumBoundaryFacing {
        IN, OUT;

        public EnumBoundaryFacing getOpposite() {
            return this == IN ? OUT : IN;
        }
    }

    public static void flagSecBoundaryForUpdate(final Chunk chunk, final BlockPos pos, final LightType lightType, final Direction dir, final EnumBoundaryFacing boundaryFacing) {
        flagChunkBoundaryForUpdate(chunk, (short) (1 << (pos.getY() >> 4)), lightType, dir, getAxisDirection(dir, pos.getX(), pos.getZ()), boundaryFacing);
    }

    public static void flagChunkBoundaryForUpdate(final Chunk chunk, final short sectionMask, final LightType lightType, final Direction dir, final Direction.AxisDirection axisDirection, final EnumBoundaryFacing boundaryFacing) {
        initNeighborLightChecks(chunk);
        ((IChunkLightingData) chunk).getNeighborLightChecks()[getFlagIndex(lightType, dir, axisDirection, boundaryFacing)] |= sectionMask;
        chunk.setModified();
    }

    public static int getFlagIndex(final LightType lightType, final int xOffset, final int zOffset, final Direction.AxisDirection axisDirection, final EnumBoundaryFacing boundaryFacing) {
        return (lightType == LightType.BLOCK ? 0 : 16) | ((xOffset + 1) << 2) | ((zOffset + 1) << 1) | (axisDirection.offset() + 1) | boundaryFacing.ordinal();
    }

    public static int getFlagIndex(final LightType lightType, final Direction dir, final Direction.AxisDirection axisDirection, final EnumBoundaryFacing boundaryFacing) {
        return getFlagIndex(lightType, dir.getOffsetX(), dir.getOffsetZ(), axisDirection, boundaryFacing);
    }

    private static Direction.AxisDirection getAxisDirection(final Direction dir, final int x, final int z) {
        return ((dir.getAxis() == Direction.Axis.X ? z : x) & 15) < 8 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE;
    }

    public static void scheduleRelightChecksForChunkBoundaries(final World world, final Chunk chunk) {
        for (final Direction dir : DirectionAccessor.getHorizontal()) {
            final int xOffset = dir.getOffsetX();
            final int zOffset = dir.getOffsetZ();

            final Chunk nChunk = world.getChunkProvider().getLoadedChunk(chunk.chunkX + xOffset, chunk.chunkX + zOffset);

            if (nChunk == null) {
                continue;
            }

            for (final LightType lightType : ENUM_SKY_BLOCK_VALUES) {
                for (final Direction.AxisDirection axisDir : ENUM_AXIS_DIRECTION_VALUES) {
                    //Merge flags upon loading of a chunk. This ensures that all flags are always already on the IN boundary below
                    mergeFlags(lightType, chunk, nChunk, dir, axisDir);
                    mergeFlags(lightType, nChunk, chunk, dir.getOpposite(), axisDir);

                    //Check everything that might have been canceled due to this chunk not being loaded.
                    //Also, pass in chunks if already known
                    //The boundary to the neighbor chunk (both ways)
                    scheduleRelightChecksForBoundary(world, chunk, nChunk, null, lightType, xOffset, zOffset, axisDir);
                    scheduleRelightChecksForBoundary(world, nChunk, chunk, null, lightType, -xOffset, -zOffset, axisDir);
                    //The boundary to the diagonal neighbor (since the checks in that chunk were aborted if this chunk wasn't loaded, see scheduleRelightChecksForBoundary)
                    scheduleRelightChecksForBoundary(world, nChunk, null, chunk, lightType, (zOffset != 0 ? axisDir.offset() : 0), (xOffset != 0 ? axisDir.offset() : 0),
                            dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
                }
            }
        }
    }

    private static void mergeFlags(final LightType lightType, final Chunk inChunk, final Chunk outChunk, final Direction dir, final Direction.AxisDirection axisDir) {
        IChunkLightingData outChunkLightingData = (IChunkLightingData) outChunk;

        if (outChunkLightingData.getNeighborLightChecks() == null) {
            return;
        }

        IChunkLightingData inChunkLightingData = (IChunkLightingData) inChunk;

        initNeighborLightChecks(inChunk);

        final int inIndex = getFlagIndex(lightType, dir, axisDir, EnumBoundaryFacing.IN);
        final int outIndex = getFlagIndex(lightType, dir.getOpposite(), axisDir, EnumBoundaryFacing.OUT);

        inChunkLightingData.getNeighborLightChecks()[inIndex] |= outChunkLightingData.getNeighborLightChecks()[outIndex];
        //no need to call Chunk.setModified() since checks are not deleted from outChunk
    }

    private static void scheduleRelightChecksForBoundary(final World world, final Chunk chunk, Chunk nChunk, Chunk sChunk, final LightType lightType, final int xOffset, final int zOffset, final Direction.AxisDirection axisDir) {
        IChunkLightingData chunkLightingData = (IChunkLightingData) chunk;

        if (chunkLightingData.getNeighborLightChecks() == null) {
            return;
        }

        final int flagIndex = getFlagIndex(lightType, xOffset, zOffset, axisDir, EnumBoundaryFacing.IN); //OUT checks from neighbor are already merged

        final int flags = chunkLightingData.getNeighborLightChecks()[flagIndex];

        if (flags == 0) {
            return;
        }

        if (nChunk == null) {
            nChunk = world.getChunkProvider().getLoadedChunk(chunk.chunkX + xOffset, chunk.chunkZ + zOffset);

            if (nChunk == null) {
                return;
            }
        }

        if (sChunk == null) {
            sChunk = world.getChunkProvider().getLoadedChunk(chunk.chunkX + (zOffset != 0 ? axisDir.offset() : 0), chunk.chunkZ + (xOffset != 0 ? axisDir.offset() : 0));

            if (sChunk == null) {
                return; //Cancel, since the checks in the corner columns require the corner column of sChunk
            }
        }

        final int reverseIndex = getFlagIndex(lightType, -xOffset, -zOffset, axisDir, EnumBoundaryFacing.OUT);

        chunkLightingData.getNeighborLightChecks()[flagIndex] = 0;

        IChunkLightingData nChunkLightingData = (IChunkLightingData) nChunk;

        if (nChunkLightingData.getNeighborLightChecks() != null) {
            nChunkLightingData.getNeighborLightChecks()[reverseIndex] = 0; //Clear only now that it's clear that the checks are processed
        }

        chunk.setModified();
        nChunk.setModified();

        //Get the area to check
        //Start in the corner...
        int xMin = chunk.chunkX << 4;
        int zMin = chunk.chunkZ << 4;

        //move to other side of chunk if the direction is positive
        if ((xOffset | zOffset) > 0) {
            xMin += 15 * xOffset;
            zMin += 15 * zOffset;
        }

        //shift to other half if necessary (shift perpendicular to dir)
        if (axisDir == Direction.AxisDirection.POSITIVE) {
            xMin += 8 * (zOffset & 1); //x & 1 is same as abs(x) for x=-1,0,1
            zMin += 8 * (xOffset & 1);
        }

        //get maximal values (shift perpendicular to dir)
        final int xMax = xMin + 7 * (zOffset & 1);
        final int zMax = zMin + 7 * (xOffset & 1);

        for (int y = 0; y < 16; ++y) {
            if ((flags & (1 << y)) != 0) {
                scheduleRelightChecksForArea(world, lightType, xMin, y << 4, zMin, xMax, (y << 4) + 15, zMax);
            }
        }
    }

    public static void initNeighborLightChecks(final Chunk chunk) {
        IChunkLightingData lightingData = (IChunkLightingData) chunk;

        if (lightingData.getNeighborLightChecks() == null) {
            lightingData.setNeighborLightChecks(new short[FLAG_COUNT]);
        }
    }

    public static final String neighborLightChecksKey = "NeighborLightChecks";

    public static void writeNeighborLightChecksToNBT(final Chunk chunk, final NbtCompound nbt) {
        short[] neighborLightChecks = ((IChunkLightingData) chunk).getNeighborLightChecks();

        if (neighborLightChecks == null) {
            return;
        }

        boolean empty = true;

        final NbtList list = new NbtList();

        for (final short flags : neighborLightChecks) {
            list.add(new NbtShort(flags));

            if (flags != 0) {
                empty = false;
            }
        }

        if (!empty) {
            nbt.put(neighborLightChecksKey, list);
        }
    }

    public static void readNeighborLightChecksFromNBT(final Chunk chunk, final NbtCompound nbt) {
        if (nbt.contains(neighborLightChecksKey, 9)) {
            final NbtList list = nbt.getList(neighborLightChecksKey, 2);

            if (list.size() == FLAG_COUNT) {
                initNeighborLightChecks(chunk);

                short[] neighborLightChecks = ((IChunkLightingData) chunk).getNeighborLightChecks();

                for (int i = 0; i < FLAG_COUNT; ++i) {
                    neighborLightChecks[i] = ((NbtShort) list.get(i)).shortValue();
                }
            }
            else {
                PhosphorMod.LOGGER.warn("Chunk field {} had invalid length, ignoring it (chunk coordinates: {} {})", neighborLightChecksKey, chunk.chunkX, chunk.chunkZ);
            }
        }
    }

    public static void initChunkLighting(final Chunk chunk, final World world) {
        final int xBase = chunk.chunkX << 4;
        final int zBase = chunk.chunkZ << 4;

        final BlockPos.Pooled pos = BlockPos.Pooled.method_12571(xBase, 0, zBase);

        if (world.isRegionLoaded(pos.add(-16, 0, -16), pos.add(31, 255, 31), false)) {
            final ChunkSection[] sections = chunk.getBlockStorage();

            for (int j = 0; j < sections.length; ++j) {
                final ChunkSection section = sections[j];

                if (section == Chunk.EMPTY) {
                    continue;
                }

                int yBase = j * 16;

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            int key = ((PaletteContainerAccessor) ((ChunkSectionAccessor) section).getData()).getStorage().get(y << 8 | z << 4 | x);

                            if (key != 0) {
                                BlockState state = ((PaletteContainerAccessor) ((ChunkSectionAccessor) section).getData()).getPalette().getStateForId(key);

                                if (state != null) {
                                    int light = state.getLuminance();

                                    if (light > 0) {
                                        pos.setPosition(xBase + x, yBase + y, zBase + z);

                                        world.method_8539(LightType.BLOCK, pos);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (world.dimension.isOverworld()) {
                ((IChunkLightingData) chunk).setSkylightUpdatedPublic();
            }

            ((IChunkLightingData) chunk).setLightInitialized(true);
        }

        pos.method_12576();
    }

    public static void checkChunkLighting(final Chunk chunk, final World world) {
        if (!((IChunkLightingData) chunk).isLightInitialized()) {
            initChunkLighting(chunk, world);
        }

        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                if (x != 0 || z != 0) {
                    Chunk nChunk = world.getChunkProvider().getLoadedChunk(chunk.chunkX + x, chunk.chunkZ + z);

                    if (nChunk == null || !((IChunkLightingData) nChunk).isLightInitialized()) {
                        return;
                    }
                }
            }
        }

        chunk.setLightPopulated(true);
    }

    public static void initSkylightForSection(final World world, final Chunk chunk, final ChunkSection section) {
        if (world.dimension.isOverworld()) {
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    if (chunk.getHighestBlockY(x, z) <= section.getYOffset()) {
                        for (int y = 0; y < 16; ++y) {
                            section.setSkyLight(x, y, z, LightType.SKY.defaultValue);
                        }
                    }
                }
            }
        }
    }

    private static short[] getNeighborLightChecks(Chunk chunk) {
        return ((IChunkLightingData) chunk).getNeighborLightChecks();
    }

    private static void setNeighborLightChecks(Chunk chunk, short[] table) {
        ((IChunkLightingData) chunk).setNeighborLightChecks(table);
    }

    public static int getCachedLightFor(Chunk chunk, LightType lightType, BlockPos pos) {
        return ((IChunkLighting) chunk).getCachedLightFor(lightType, pos);
    }

    public static ILightingEngine getLightingEngine(World world) {
        return ((ILightingEngineProvider) world).getLightingEngine();
    }

}
