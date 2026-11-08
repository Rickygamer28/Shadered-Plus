package com.example.skyblocks.client;

import com.example.skyblocks.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The kaleidoscope as an item: its brass frame and its windows (KaleidoscopeRenderer). */
public class KaleidoscopeItemRenderer extends BlockEntityWithoutLevelRenderer {
    public KaleidoscopeItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(ModBlocks.KALEIDOSCOPE.get().defaultBlockState(), poseStack, buffers, light, overlay);
        KaleidoscopeRenderer.drawItem(poseStack, buffers);
    }
}
