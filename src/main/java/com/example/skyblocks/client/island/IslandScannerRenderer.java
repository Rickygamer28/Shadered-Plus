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
 * While the scanner's menu is open: outlines the area it will scan (white) and the ground layer, where the
 * floating-island underside starts (green).
 */
public class IslandScannerRenderer implements BlockEntityRenderer<IslandScannerBlockEntity> {

    public IslandScannerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(IslandScannerBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (!(Minecraft.getInstance().screen instanceof IslandScannerScreen screen) || !screen.pos().equals(be.getBlockPos())) {
            return;
        }
        int r = screen.size() / 2;
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, -r, -1 - screen.depth(), -r, r + 1, screen.above(), r + 1,
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
