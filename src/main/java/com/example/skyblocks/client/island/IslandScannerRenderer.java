package com.example.skyblocks.client.island;

import com.example.skyblocks.island.IslandScannerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/**
 * When the scanner's Area button is on: outlines the area it will scan (white) and the ground layer, where the
 * floating-island underside starts (green). While its menu is open, the outline follows the menu's values.
 */
public class IslandScannerRenderer implements BlockEntityRenderer<IslandScannerBlockEntity> {

    public IslandScannerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(IslandScannerBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        int size, above, depth;
        if (Minecraft.getInstance().screen instanceof IslandScannerScreen screen && screen.pos().equals(be.getBlockPos())) {
            if (!screen.showArea()) {
                return;
            }
            size = screen.size();
            above = screen.above();
            depth = screen.depth();
        } else {
            if (!be.showArea()) {
                return;
            }
            size = be.size();
            above = be.above();
            depth = be.depth();
        }
        int r = size / 2;
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, -r, -1 - depth, -r, r + 1, above, r + 1,
                1.0F, 1.0F, 1.0F, 1.0F);
        LevelRenderer.renderLineBox(poseStack, lines, -r, -1, -r, r + 1, 0, r + 1, 0.4F, 1.0F, 0.5F, 1.0F);
    }

    @Override
    public AABB getRenderBoundingBox(IslandScannerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(13, 26, 13);
    }

    @Override
    public boolean shouldRenderOffScreen(IslandScannerBlockEntity be) {
        return true;
    }
}
