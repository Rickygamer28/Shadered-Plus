package com.example.skyblocks.client;

import com.example.skyblocks.InfusionAltarBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The item on the infusion altar, floating over it and slowly turning (its painted shaders drawn with it). */
public class InfusionAltarRenderer implements BlockEntityRenderer<InfusionAltarBlockEntity> {
    public InfusionAltarRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(InfusionAltarBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack item = be.item();
        if (item.isEmpty() || be.getLevel() == null) {
            return;
        }
        float t = be.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5F, 1.25F + Mth.sin(t * 0.06F) * 0.05F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(t * 1.5F));
        pose.scale(0.75F, 0.75F, 0.75F);
        int lit = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, lit, overlay, pose, buffers, be.getLevel(),
                (int) be.getBlockPos().asLong());
        pose.popPose();
    }
}
