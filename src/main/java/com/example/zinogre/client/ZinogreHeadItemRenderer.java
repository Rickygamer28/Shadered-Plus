package com.example.zinogre.client;

import com.example.zinogre.ZinogreMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The head item drawn as the 3D head: in the inventory, in hand, on the ground, in item frames, and worn on the head
 * (Minecraft draws a worn non-skull block item with its item renderer, like a carved pumpkin).
 */
public class ZinogreHeadItemRenderer extends BlockEntityWithoutLevelRenderer {
    public ZinogreHeadItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer,
                             int packedLight, int packedOverlay) {
        boolean stygian = stack.is(ZinogreMod.STYGIAN_HEAD_ITEM.get());
        ZinogreHeadRenderer.drawHeadInCube(poseStack, buffer, packedLight, stygian);
    }
}
