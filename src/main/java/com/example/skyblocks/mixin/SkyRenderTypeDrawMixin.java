package com.example.skyblocks.mixin;

import com.example.skyblocks.client.SkyUsage;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shadered's sky render types (sky block items, Illusorite... drawn in the world): just before a batch of them is
 * drawn, its sky's picture is brought up to date only where the batch is on screen (SkyUsage.itemsDrawn), instead of
 * the whole picture being drawn every frame for them.
 */
@Mixin(RenderType.class)
public abstract class SkyRenderTypeDrawMixin {
    @Inject(method = "draw", at = @At("HEAD"), require = 0)
    private void skyblocks$skyPart(MeshData mesh, CallbackInfo ci) {
        SkyUsage.itemsDrawn((RenderType) (Object) this, mesh);
    }

    @Inject(method = "draw", at = @At("RETURN"), require = 0)
    private void skyblocks$skyPartDone(MeshData mesh, CallbackInfo ci) {
        SkyUsage.itemsDrawnDone();
    }
}
