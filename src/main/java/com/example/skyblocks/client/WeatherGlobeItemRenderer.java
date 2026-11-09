package com.example.skyblocks.client;

import com.example.skyblocks.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The weather globe as an item: its wooden base and the glass ball with its little world (WeatherGlobeRenderer). */
public class WeatherGlobeItemRenderer extends BlockEntityWithoutLevelRenderer {
    public WeatherGlobeItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(ModBlocks.WEATHER_GLOBE.get().defaultBlockState(), poseStack, buffers, light, overlay);
        WeatherGlobeRenderer.drawItem(poseStack, buffers);
    }
}
