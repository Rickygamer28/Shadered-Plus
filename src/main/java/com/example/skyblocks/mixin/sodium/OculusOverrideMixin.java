package com.example.skyblocks.mixin.sodium;

import com.example.skyblocks.client.MirrorRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a mirror draws its picture, Oculus doesn't swap shaders for the pack's: otherwise a shader of ours (or
 * Shadered's) made Oculus bind its own framebuffers, and the mirror's things were drawn into the real world.
 */
@Pseudo // (Oculus may not be installed)
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class OculusOverrideMixin {
    @Inject(method = "shouldOverrideShaders()Z", at = @At("RETURN"), cancellable = true, require = 0)
    private void skyblocks$notInMirrors(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && MirrorRenderer.isRenderingReflection()) {
            cir.setReturnValue(false);
        }
    }
}
