package com.example.skyblocks.mixin;

import com.example.skyblocks.client.ShaderPaintRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shaders painted onto items (ShaderPaintRender): drawn over an item's own model, once per item drawn, with the buffers
 * the item is being drawn into.
 */
@Mixin(value = ItemRenderer.class, remap = false)
public abstract class ItemRendererPaintMixin {
    @Unique
    private static MultiBufferSource skyblocks$buffers;
    @Unique
    private static boolean skyblocks$pending;
    @Unique
    private static boolean skyblocks$gui;

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"), require = 0)
    private void skyblocks$startItem(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light,
                                     int overlay, BakedModel model, CallbackInfo ci) {
        skyblocks$buffers = buffers;
        skyblocks$pending = true;
        skyblocks$gui = context == ItemDisplayContext.GUI;
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("RETURN"), require = 0)
    private void skyblocks$endItem(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light,
                                   int overlay, BakedModel model, CallbackInfo ci) {
        skyblocks$buffers = null;
        skyblocks$pending = false;
    }

    @Inject(method = "renderModelLists", at = @At("TAIL"), require = 0)
    private void skyblocks$afterModel(BakedModel model, ItemStack stack, int light, int overlay, PoseStack pose, VertexConsumer buffer, CallbackInfo ci) {
        if (skyblocks$pending) {
            skyblocks$pending = false;
            ShaderPaintRender.afterModel(stack, model, pose, skyblocks$buffers, skyblocks$gui);
        }
    }
}
