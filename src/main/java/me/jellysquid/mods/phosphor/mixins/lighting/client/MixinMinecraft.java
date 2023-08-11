package me.jellysquid.mods.phosphor.mixins.lighting.client;

import me.jellysquid.mods.phosphor.api.ILightingEngineProvider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraft {
    @Shadow
    @Final
    public Profiler profiler;

    @Shadow
    public ClientWorld world;

    /**
     * Forces the client to process light updates before rendering the world. We inject before the call to the profiler
     * which designates the start of world rendering. This is a rather injection site.
     *
     * @author JellySquid
     */
    @Inject(method = "tick", at = @At(value = "CONSTANT", args = "stringValue=levelRenderer", shift = At.Shift.BY, by = -3))
    private void onRunTick(CallbackInfo ci) {
        this.profiler.swap("lighting");

        ((ILightingEngineProvider) this.world).getLightingEngine().processLightUpdates();
    }

}
