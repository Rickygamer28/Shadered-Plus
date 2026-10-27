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

/** The lava lamps as items: the real lamp - its metal model and the moving wax in the glass (LavaLampRenderer). */
public class LavaLampItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final boolean big;

    public LavaLampItemRenderer(boolean big) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.big = big;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers,
                             int light, int overlay) {
        var blocks = Minecraft.getInstance().getBlockRenderer();
        poseStack.pushPose();
        if (big) { // (two blocks tall: shrunk to fit where a block goes)
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.scale(0.6F, 0.6F, 0.6F);
            poseStack.translate(-0.5F, 0.0F, -0.5F);
            var lower = ModBlocks.BIG_LAVA_LAMP.get().defaultBlockState();
            blocks.renderSingleBlock(lower, poseStack, buffers, light, overlay);
            poseStack.pushPose();
            poseStack.translate(0.0F, 1.0F, 0.0F);
            blocks.renderSingleBlock(lower.setValue(BigLavaLampBlock.HALF, DoubleBlockHalf.UPPER), poseStack, buffers, light, overlay);
            poseStack.popPose();
        } else {
            blocks.renderSingleBlock(ModBlocks.LAVA_LAMP.get().defaultBlockState(), poseStack, buffers, light, overlay);
        }
        LavaLampRenderer.drawItem(poseStack, buffers, big, 0xFF6A1A);
        poseStack.popPose();
    }
}
