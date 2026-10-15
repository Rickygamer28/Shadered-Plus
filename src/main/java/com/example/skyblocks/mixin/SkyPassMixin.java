package com.example.skyblocks.mixin;

import com.example.skyblocks.client.SkyUsage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.noodlegamer76.shadered.client.renderer.complexpasses.EndBoxRenderPass;
import com.noodlegamer76.shadered.client.renderer.complexpasses.EndSkySkyboxRenderPass;
import com.noodlegamer76.shadered.client.renderer.complexpasses.MimicSkyboxRenderPass;
import com.noodlegamer76.shadered.client.renderer.complexpasses.SkyboxRenderPass;
import com.noodlegamer76.shadered.client.util.RenderStage;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shadered's sky passes (its skies and ours): skip drawing a sky's screen-sized picture while nothing uses that sky,
 * and count asking for the picture as using it (see SkyUsage).
 */
@Mixin({SkyboxRenderPass.class, EndSkySkyboxRenderPass.class, EndBoxRenderPass.class, MimicSkyboxRenderPass.class})
public abstract class SkyPassMixin {
    @Shadow
    @Final
    private SkyblockBatchData batchData;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyblocks$skipUnused(RenderStage stage, PoseStack poseStack, int renderTick, float partialTick, CallbackInfo ci) {
        if (!SkyUsage.recentlyUsed(batchData)) {
            ci.cancel();
        }
    }

    @Inject(method = "getSkyboxTarget", at = @At("HEAD"), require = 0)
    private void skyblocks$targetWanted(CallbackInfoReturnable<?> cir) {
        SkyUsage.mark(batchData);
    }
}
