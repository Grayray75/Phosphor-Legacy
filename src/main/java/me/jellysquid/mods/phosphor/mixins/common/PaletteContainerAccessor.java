package me.jellysquid.mods.phosphor.mixins.common;

import net.minecraft.util.BitStorage;
import net.minecraft.world.chunk.Palette;
import net.minecraft.world.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PalettedContainer.class)
public interface PaletteContainerAccessor {
    @Accessor("storage")
    BitStorage getStorage();

    @Accessor("palette")
    Palette getPalette();
}
