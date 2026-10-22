package com.example.skyblocks.mixin.sodium;

import com.example.skyblocks.client.iris.MirrorIrisState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A shader pack program uploads its once-a-frame uniforms (the view matrices among them) again when a mirror swaps
 * those matrices, and again when they're put back (see MirrorIrisState).
 */
@Pseudo // (Oculus may not be installed)
@Mixin(targets = "net.irisshaders.iris.gl.program.ProgramUniforms", remap = false)
public abstract class OculusUniformsMixin {
    @Shadow(remap = false)
    int lastFrame;

    @Unique
    private int skyblocks$epoch;

    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void skyblocks$mirrorMatrices(CallbackInfo ci) {
        int epoch = MirrorIrisState.epoch;
        if (skyblocks$epoch != epoch) {
            skyblocks$epoch = epoch;
            lastFrame = -1;
        }
    }
}
