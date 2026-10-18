package com.example.skyblocks.mixin;

import com.example.skyblocks.client.SkyUsage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.noodlegamer76.shadered.client.renderer.complexpasses.SkyblockRenderPass;
import com.noodlegamer76.shadered.client.util.RenderStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Just before Shadered draws the queued sky blocks: a sky that was resting (not drawn at the start of this frame) and
 * now has blocks in view gets its picture drawn first, so it never shows an out-of-date one (see SkyUsage).
 */
@Mixin(value = SkyblockRenderPass.class, remap = false)
public abstract class SkyblockRenderPassMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0, remap = false)
    private void skyblocks$freshSkies(RenderStage stage, PoseStack poseStack, int renderTick, float partialTick, CallbackInfo ci) {
        SkyUsage.ensureQueuedFresh();
    }
}
