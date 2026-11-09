package com.example.skyblocks.client;

import com.example.skyblocks.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The lava lamp pillar as an item: its foot and cap, and the glass with the wax moving in it (LavaPillarRenderer). */
public class LavaPillarItemRenderer extends BlockEntityWithoutLevelRenderer {
    public LavaPillarItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(ModBlocks.LAVA_PILLAR.get().defaultBlockState(), poseStack, buffers, light, overlay);
        LavaPillarRenderer.drawItem(poseStack, buffers);
    }
}
