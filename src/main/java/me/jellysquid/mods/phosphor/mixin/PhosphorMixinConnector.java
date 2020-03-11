package me.jellysquid.mods.phosphor.mixin;

import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

public class PhosphorMixinConnector implements IMixinConnector {
    @Override
    public void connect() {
        Mixins.addConfiguration("phosphor.mixins.json");
    }
}
