package com.example.skyblocks.mixin.sodium;

import com.example.skyblocks.client.MirrorRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a mirror draws, Embeddium's chunk sections outside that mirror's view are skipped (no faces to draw): a mirror
 * then costs what it shows, not the whole loaded world.
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class SodiumMirrorCullMixin {
    @Inject(method = "getVisibleFaces(IIIIII)I", at = @At("HEAD"), cancellable = true, require = 0)
    private static void skyblocks$onlyInMirror(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ,
                                               CallbackInfoReturnable<Integer> cir) {
        if (MirrorRenderer.isRenderingReflection() && !MirrorRenderer.sectionInMirror(chunkX, chunkY, chunkZ)) {
            cir.setReturnValue(0);
        }
    }
}
