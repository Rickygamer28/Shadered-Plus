package com.example.skyblocks.mixin.sodium;

import com.example.skyblocks.client.MirrorRenderer;
import com.example.skyblocks.client.iris.MirrorPackSamplers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right after Oculus binds a shader pack's chunk program and its textures: while a mirror draws, textures that only
 * make sense for the real view are swapped for harmless ones (see MirrorPackSamplers).
 */
@Pseudo // (Oculus may not be installed)
@Mixin(targets = "net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkShaderInterface", remap = false)
public abstract class OculusChunkSamplerMixin {
    @Inject(method = "setupState", at = @At("TAIL"), require = 0)
    private void skyblocks$mirrorSamplers(CallbackInfo ci) {
        if (MirrorRenderer.isRenderingReflection()) {
            MirrorPackSamplers.apply();
        }
    }
}
