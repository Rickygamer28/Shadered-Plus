package com.example.skyblocks.mixin;

import com.example.skyblocks.client.WorldTiming;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times each block entity drawn in the normal view, by kind, for the F3 screen (WorldTiming). */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityTimingMixin {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), require = 0)
    private void skyblocks$timeStart(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        WorldTiming.blockEntityStart();
    }

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("RETURN"), require = 0)
    private void skyblocks$timeEnd(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        WorldTiming.blockEntityEnd(be);
    }
}
