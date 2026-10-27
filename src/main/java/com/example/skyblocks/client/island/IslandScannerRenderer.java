package com.example.skyblocks.client.island;

import com.example.skyblocks.island.IslandScan;
import com.example.skyblocks.island.IslandScannerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
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
        boolean showArea;
        if (Minecraft.getInstance().screen instanceof IslandScannerScreen screen && screen.pos().equals(be.getBlockPos())) {
            size = screen.size();
            above = screen.above();
            depth = screen.depth();
            showArea = screen.showArea();
        } else {
            size = be.size();
            above = be.above();
            depth = be.depth();
            showArea = be.showArea();
        }
        renderPreview(be, partialTick, poseStack, buffer, size, above, depth);
        if (!showArea) {
            return;
        }
        int r = size / 2;
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, -r, -1 - depth, -r, r + 1, above, r + 1,
                1.0F, 1.0F, 1.0F, 1.0F);
        LevelRenderer.renderLineBox(poseStack, lines, -r, -1, -r, r + 1, 0, r + 1, 0.4F, 1.0F, 0.5F, 1.0F);
    }

    /** How often the area is checked for changes (ticks); the preview is rebuilt only when something changed. */
    private static final int PREVIEW_REFRESH = 20;
    /** The preview's block entities (chests, sky blocks, cats...) are drawn up to this far away. */
    private static final double PREVIEW_DETAIL = 10.0;
    /** The preview is this many blocks wide, floating above the lens. */
    private static final float PREVIEW_WIDTH = 0.9F;

    /** The live preview of the island floating above the scanner's lens, slowly turning. */
    private static void renderPreview(IslandScannerBlockEntity be, float partialTick, PoseStack poseStack,
                                      MultiBufferSource buffer, int size, int above, int depth) {
        Level level = be.getLevel();
        Minecraft mc = Minecraft.getInstance();
        if (level == null || mc.player == null
                || mc.player.distanceToSqr(be.getBlockPos().getCenter()) > 48 * 48) {
            return;
        }
        long time = level.getGameTime();
        int key = (size * 31 + above) * 31 + depth;
        IslandView view = be.clientPreview instanceof IslandView v ? v : null;
        boolean due = time - be.previewTime >= PREVIEW_REFRESH || time < be.previewTime;
        if (view != null && key == be.previewKey && due) {
            // time to look again: only rebuild if a block in the area actually changed
            long hash = IslandView.previewHash(level, be.getBlockPos(), size, above, depth);
            if (hash == be.previewHash) {
                be.previewTime = time;
                due = false;
            }
        }
        if (view == null || key != be.previewKey || due) {
            try {
                be.previewHash = IslandView.previewHash(level, be.getBlockPos(), size, above, depth);
                view = IslandView.preview(level, be.getBlockPos(), size, above, depth);
            } catch (RuntimeException e) {
                return; // keep the old one, if any, until the next try
            }
            if (be.clientPreview instanceof IslandView old) {
                old.close(); // frees its blocks on the graphics card
            }
            be.clientPreview = view;
            be.previewKey = key;
            be.previewTime = time;
        }
        IslandMesh mesh;
        try {
            mesh = view.mesh();
        } catch (RuntimeException e) {
            return;
        }
        // Same block size for every area (like the painting): the largest area (25 wide) fills the preview width.
        float scale = PREVIEW_WIDTH / IslandScan.MAX_SIZE;
        float spin = ((time % 3600L) + partialTick) * 0.5F;
        float bob = Mth.sin(((time % 24000L) + partialTick) * 0.06F) * 0.03F;
        poseStack.pushPose();
        poseStack.translate(0.5F, 1.12F + bob, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-view.contentCenterX(), -view.lowestLayer(), -view.contentCenterZ());
        mesh.render(poseStack.last(), buffer);
        // (in a mirror, or more than a few blocks off - the preview's blocks are a few pixels each: just the blocks)
        if (!IslandRendering.inMirror && mc.player.distanceToSqr(be.getBlockPos().getCenter()) < PREVIEW_DETAIL * PREVIEW_DETAIL) {
            view.renderBlockEntities(poseStack, buffer, partialTick);
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(IslandScannerBlockEntity be) {
        return true;
    }
}
