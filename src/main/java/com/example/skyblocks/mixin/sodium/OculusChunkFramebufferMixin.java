package com.example.skyblocks.mixin.sodium;

import com.example.skyblocks.client.MirrorRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With an Oculus shader pack, each chunk pass starts by binding Oculus's own framebuffer (and Oculus replaces
 * Embeddium's whole begin(), so a hook there never runs). While a mirror draws, its picture is bound again right after.
 */
@Pseudo // (Oculus may not be installed)
@Mixin(targets = "net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides", remap = false)
public abstract class OculusChunkFramebufferMixin {
    @Inject(method = "bindFramebuffer", at = @At("TAIL"), require = 0)
    private void skyblocks$intoMirror(CallbackInfo ci) {
        if (MirrorRenderer.isRenderingReflection()) {
            MirrorRenderer.rebindPicture();
        }
    }
}
