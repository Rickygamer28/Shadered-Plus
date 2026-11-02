package com.example.skyblocks.client;

import com.example.skyblocks.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The plasma ball as an item: its base model and the live globe (PlasmaBallRenderer). */
public class PlasmaBallItemRenderer extends BlockEntityWithoutLevelRenderer {
    public PlasmaBallItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers,
                             int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(ModBlocks.PLASMA_BALL.get().defaultBlockState(), poseStack, buffers, light, overlay);
        PlasmaBallRenderer.drawItem(poseStack, buffers, 0xC45CFF);
    }
}
