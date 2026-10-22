package com.example.skyblocks.mixin.sodium;

import com.example.skyblocks.client.MirrorRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a mirror draws, chunks are drawn with Embeddium's own terrain program, not the shader pack's: the pack's
 * program works out depth-based effects with the real camera's projection, which doesn't match a mirror's (its near
 * plane is the mirror), and lost the ground next to the mirror (a sky-coloured strip at its base). Oculus's chunk
 * meshes keep Embeddium's layout at the start of each vertex, so Embeddium's program reads them as usual.
 */
@Pseudo // (Oculus may not be installed)
@Mixin(targets = "net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides", remap = false)
public abstract class OculusChunkProgramMixin {
    @Inject(method = "getProgramOverride", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyblocks$plainInMirror(CallbackInfoReturnable<Object> cir) {
        if (MirrorRenderer.isRenderingReflection()) {
            cir.setReturnValue(null);
        }
    }
}
