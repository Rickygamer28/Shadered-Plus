package com.example.skyblocks.mixin;

import com.example.skyblocks.client.WorldTiming;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times each mob drawn in the normal view, by kind, for the F3 screen (WorldTiming). */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityTimingMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void skyblocks$timeStart(Entity entity, double x, double y, double z, float yaw, float partialTick, PoseStack pose,
                                     MultiBufferSource buffers, int light, CallbackInfo ci) {
        WorldTiming.mobStart();
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void skyblocks$timeEnd(Entity entity, double x, double y, double z, float yaw, float partialTick, PoseStack pose,
                                   MultiBufferSource buffers, int light, CallbackInfo ci) {
        WorldTiming.mobEnd(entity);
    }
}
