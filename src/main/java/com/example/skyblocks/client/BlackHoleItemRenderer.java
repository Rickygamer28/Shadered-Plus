package com.example.skyblocks.client;

import com.example.skyblocks.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The black holes as items: the small one on its pedestal; the big one shrunk to fit where a block goes. */
public class BlackHoleItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final boolean big;

    public BlackHoleItemRenderer(boolean big) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.big = big;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers,
                             int light, int overlay) {
        poseStack.pushPose();
        if (big) {
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.scale(0.33F, 0.33F, 0.33F);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
        } else {
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(ModBlocks.BLACK_HOLE.get().defaultBlockState(), poseStack, buffers, light, overlay);
        }
        BlackHoleRenderer.drawItem(poseStack, buffers, big);
        poseStack.popPose();
    }
}
