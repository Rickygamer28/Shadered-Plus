package com.example.skyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/** The disco ball item, in hand, in the inventory, in frames: the 3D ball, spinning and glinting. */
public class DiscoBallItemRenderer extends BlockEntityWithoutLevelRenderer {
    public DiscoBallItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers,
                             int light, int overlay) {
        double seconds = (System.nanoTime() / 1.0e9) % 100000.0;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(1.5F, 1.5F, 1.5F); // (a bit bigger than the block's ball, so it reads as an item)
        DiscoBallRenderer.drawBall(buffers.getBuffer(DiscoBallRenderer.Types.BALL), poseStack.last().pose(), new Vector3f(),
                new Vector3f(0.3F, 0.4F, 1.0F).normalize(), (float) (seconds * 0.8), 1, seconds, false);
        poseStack.popPose();
    }
}
