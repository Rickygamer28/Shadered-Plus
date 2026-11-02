package com.example.skyblocks.client;

import com.example.skyblocks.ModBlocks;
import com.example.skyblocks.SmallTankBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** A small fish tank as an item: its block model (the tall tank's base and cap) and the live tank (SmallTankRenderer). */
public class SmallTankItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final int shape;

    public SmallTankItemRenderer(int shape) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.shape = shape;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers,
                             int light, int overlay) {
        if (shape == SmallTankBlock.TALL) {
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(ModBlocks.TALL_TANK.get().defaultBlockState(), poseStack, buffers, light, overlay);
        }
        SmallTankRenderer.drawItem(poseStack, buffers, shape);
    }
}
