package com.example.skyblocks.client;

import com.example.skyblocks.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The singularity as an item: its frame, and its hole as small as an empty one. */
public class SingularityItemRenderer extends BlockEntityWithoutLevelRenderer {
    public SingularityItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(ModBlocks.SINGULARITY.get().defaultBlockState(), poseStack, buffers, light, overlay);
        BlackHoleRenderer.drawItem(poseStack, buffers, 2, 0.0F, 0x9A6BFF);
    }
}
