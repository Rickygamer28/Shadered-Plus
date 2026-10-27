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
public abstract class SkyPassMixin implements SkyUsage.Pass {
    @Shadow(remap = false)
    @Final
    private SkyblockBatchData batchData;

    @org.spongepowered.asm.mixin.Unique
    private long skyblocks$drawnFrame = -1;

    @Override
    public long skyblocks$drawnFrame() {
        return skyblocks$drawnFrame;
    }

    @Override
    public void skyblocks$setDrawnFrame(long frame) {
        skyblocks$drawnFrame = frame;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void skyblocks$skipUnused(RenderStage stage, PoseStack poseStack, int renderTick, float partialTick, CallbackInfo ci) {
        if (stage == RenderStage.AFTER_SKY) {
            SkyUsage.skyStage(renderTick, partialTick);
        }
        if (!SkyUsage.drawAtSkyStage(batchData)) {
            ci.cancel(); // (unused; or only sky blocks use it: drawn just before them, where they are - SkyUsage)
            return;
        }
        skyblocks$drawnFrame = SkyUsage.frame(); // drawn this frame (at the sky stage or on demand)
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0, remap = false)
    private void skyblocks$skyStageDone(RenderStage stage, PoseStack poseStack, int renderTick, float partialTick, CallbackInfo ci) {
        SkyUsage.endSkyStageDraw(); // (a hotbar-only picture's scissor)
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean skyblocks$announced;

    @Inject(method = "<init>*", at = @At("TAIL"), require = 0, remap = false)
    private void skyblocks$announce(CallbackInfo ci) {
        SkyUsage.register(batchData, this);
        if (!skyblocks$announced) {
            skyblocks$announced = true;
            com.example.skyblocks.SkyBlocksMod.LOGGER.info("Shadered+: unused sky pictures are skipped (sky pass tracking active)");
        }
    }

    @Inject(method = "getSkyboxTarget", at = @At("HEAD"), require = 0, remap = false)
    private void skyblocks$targetWanted(CallbackInfoReturnable<?> cir) {
        SkyUsage.mark(batchData);
    }
}
