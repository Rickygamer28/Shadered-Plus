package com.example.skyblocks.client;

import com.example.skyblocks.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** An orrery or planetarium as an item: its base and the live model (SolarSystemRenderer). */
public class SolarSystemItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final boolean hologram;

    public SolarSystemItemRenderer(boolean hologram) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.hologram = hologram;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers,
                             int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock((hologram ? ModBlocks.PLANETARIUM : ModBlocks.ORRERY).get().defaultBlockState(),
                poseStack, buffers, light, overlay);
        SolarSystemRenderer.drawItem(poseStack, buffers, hologram);
    }
}
