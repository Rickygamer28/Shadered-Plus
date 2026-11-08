package com.example.skyblocks.mixin;

import com.example.skyblocks.client.SkyUsage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Shadered's sky render types (sky block items, Illusorite... drawn in the world): just before a batch of them is
 * drawn, its sky's picture is brought up to date only where the batch is on screen (SkyUsage.itemsDrawn), instead of
 * the whole picture being drawn every frame for them. 1.20.1: RenderType.end(BufferBuilder, VertexSorting) finishes the
 * batch itself - caught once it's finished, before the render type is set up (as 1.21.1's draw(MeshData) at its head).
 */
@Mixin(RenderType.class)
public abstract class SkyRenderTypeDrawMixin {
    @Inject(method = "end", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;setupRenderState()V"),
            locals = LocalCapture.CAPTURE_FAILSOFT, require = 0)
    private void skyblocks$skyPart(BufferBuilder builder, VertexSorting sorting, CallbackInfo ci, BufferBuilder.RenderedBuffer mesh) {
        SkyUsage.itemsDrawn((RenderType) (Object) this, mesh);
    }

    @Inject(method = "end", at = @At("RETURN"), require = 0)
    private void skyblocks$skyPartDone(BufferBuilder builder, VertexSorting sorting, CallbackInfo ci) {
        SkyUsage.itemsDrawnDone();
    }
}
