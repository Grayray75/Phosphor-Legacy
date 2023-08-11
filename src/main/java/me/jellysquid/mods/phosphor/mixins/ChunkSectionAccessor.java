package me.jellysquid.mods.phosphor.mixins;

import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.palette.PaletteContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkSection.class)
public interface ChunkSectionAccessor {
    @Accessor("field_12914")
    PaletteContainer getData();
}
