package me.jellysquid.mods.phosphor.mixins;

import net.minecraft.world.chunk.palette.Palette;
import net.minecraft.world.chunk.palette.PaletteContainer;
import net.minecraft.world.chunk.palette.PaletteData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PaletteContainer.class)
public interface PaletteContainerAccessor {
    @Accessor("paletteData")
    PaletteData getStorage();

    @Accessor("palette")
    Palette getPalette();
}
