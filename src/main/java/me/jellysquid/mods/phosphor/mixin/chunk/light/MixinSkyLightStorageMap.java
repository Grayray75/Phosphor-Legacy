package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.jellysquid.mods.phosphor.common.chunk.light.SkyLightStorageMapAccess;
import net.minecraft.world.lighting.SkyLightStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SkyLightStorage.StorageMap.class)
public class MixinSkyLightStorageMap implements SkyLightStorageMapAccess {
    @Shadow
    private int field_215652_b;

    @Shadow
    @Final
    private Long2IntOpenHashMap field_215653_c;

    @Override
    public int getDefaultHeight() {
        return this.field_215652_b;
    }

    @Override
    public Long2IntOpenHashMap getHeightMap() {
        return this.field_215653_c;
    }
}
