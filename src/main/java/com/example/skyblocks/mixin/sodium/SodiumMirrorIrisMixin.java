package com.example.skyblocks.mixin.sodium;

import com.example.skyblocks.client.MirrorRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With an Iris shader pack, Sodium's chunk drawing starts by binding Iris's own framebuffers (and the pack's terrain
 * program). While a mirror draws, its picture is bound again right after, so the chunks go into the mirror (drawn
 * with the pack's terrain program: its colors, without the pack's later passes).
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class SodiumMirrorIrisMixin {
    @Inject(method = "begin", at = @At("TAIL"), require = 0)
    private void skyblocks$intoMirror(CallbackInfo ci) {
        if (MirrorRenderer.isRenderingReflection()) {
            MirrorRenderer.rebindPicture();
        }
    }
}
