package com.example.skyblocks.mixin.sodium;

import com.example.skyblocks.client.MirrorRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium's chunk search while a portal mirror is in view: no occlusion culling (it only keeps chunks the camera could
 * see past what's in the way, and the world through a portal can be behind a hill or a wall), so the chunks the
 * portal's picture needs are in the list (client/MirrorRenderer).
 */
@Pseudo // (Embeddium may not be installed)
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class SodiumPortalOcclusionMixin {
    @Inject(method = "shouldUseOcclusionCulling", at = @At("RETURN"), cancellable = true, require = 0)
    private void skyblocks$noOcclusionThroughPortals(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && MirrorRenderer.portalInView()) {
            cir.setReturnValue(false);
        }
    }
}
