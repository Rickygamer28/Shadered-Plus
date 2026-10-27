package com.example.skyblocks.mixin;

import com.example.skyblocks.client.WorldTiming;
import com.mojang.blaze3d.vertex.PoseStack;
import com.noodlegamer76.shadered.client.renderer.ComplexPassRenderer;
import com.noodlegamer76.shadered.client.util.RenderStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times Shadered's passes at each of its stages, for the F3 screen (WorldTiming). */
@Mixin(ComplexPassRenderer.class)
public abstract class ComplexPassTimingMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0, remap = false)
    private void skyblocks$timeStart(RenderStage stage, PoseStack poseStack, int renderTick, float partialTick, CallbackInfo ci) {
        WorldTiming.shaderedStart(stage);
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0, remap = false)
    private void skyblocks$timeEnd(RenderStage stage, PoseStack poseStack, int renderTick, float partialTick, CallbackInfo ci) {
        WorldTiming.shaderedEnd();
    }
}
