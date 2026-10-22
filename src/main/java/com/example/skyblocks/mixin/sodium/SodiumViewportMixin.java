package com.example.skyblocks.mixin.sodium;

import com.example.skyblocks.client.MirrorCulling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium's chunk search: a chunk section outside the camera's view but seen in a mirror counts as visible, so it is
 * in the chunk list the mirror's reflection draws (client/MirrorRenderer). Only applied when Embeddium is installed
 * (skyblocks.sodium.mixins.json is optional).
 */
@Pseudo // (Embeddium may not be installed)
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.viewport.Viewport", remap = false)
public abstract class SodiumViewportMixin {
    @Inject(method = "isBoxVisible(IIIF)Z", at = @At("RETURN"), cancellable = true, require = 0)
    private void skyblocks$seenInMirror(int x, int y, int z, float size, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && MirrorCulling.visible(x, y, z)) {
            cir.setReturnValue(true);
        }
    }
}
