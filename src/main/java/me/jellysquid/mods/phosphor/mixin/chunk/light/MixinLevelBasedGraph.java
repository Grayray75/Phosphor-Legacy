package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedLevelPropagator;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.lighting.LevelBasedGraph;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelBasedGraph.class)
public abstract class MixinLevelBasedGraph implements ExtendedLevelPropagator {
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
}
