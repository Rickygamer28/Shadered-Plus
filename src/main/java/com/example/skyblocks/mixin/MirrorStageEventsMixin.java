package com.example.skyblocks.mixin;

import com.example.skyblocks.client.MirrorRenderer;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drawing the world's blocks again for a mirror (the game's own chunk-layer method, or Sodium's replacement of it)
 * also fires the "a layer was drawn" render events, so other mods (and Shadered's sky blocks) would draw their own
 * things into the mirror picture with the real camera, switching framebuffers as they go. Not while a mirror draws.
 */
@Mixin(value = ClientHooks.class, remap = false)
public abstract class MirrorStageEventsMixin {
    @Inject(method = "dispatchRenderStage", at = @At("HEAD"), cancellable = true, require = 0)
    private static void skyblocks$notInMirrors(CallbackInfo ci) {
        if (MirrorRenderer.isRenderingReflection()) {
            ci.cancel();
        }
    }
}
