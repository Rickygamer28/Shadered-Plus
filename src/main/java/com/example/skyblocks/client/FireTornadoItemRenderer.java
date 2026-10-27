package com.example.skyblocks.client;

import com.example.skyblocks.BigLavaLampBlock;
import com.example.skyblocks.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** The fire tornado as an item: its real case and the whirling 3D fire inside (WallFireRenderer). */
public class FireTornadoItemRenderer extends BlockEntityWithoutLevelRenderer {
    public FireTornadoItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers,
                             int light, int overlay) {
        var blocks = Minecraft.getInstance().getBlockRenderer();
        var lower = ModBlocks.FIRE_TORNADO.get().defaultBlockState();
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F); // (two blocks tall: shrunk to fit where a block goes)
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        WallFireRenderer.drawTornadoItem(poseStack, buffers, 0xFF8A1E);
        blocks.renderSingleBlock(lower, poseStack, buffers, light, overlay);
        poseStack.pushPose();
        poseStack.translate(0.0F, 1.0F, 0.0F);
        blocks.renderSingleBlock(lower.setValue(BigLavaLampBlock.HALF, DoubleBlockHalf.UPPER), poseStack, buffers, light, overlay);
        poseStack.popPose();
        poseStack.popPose();
    }
}
