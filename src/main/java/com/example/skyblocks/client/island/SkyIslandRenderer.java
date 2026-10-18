package com.example.skyblocks.client.island;

import com.example.skyblocks.island.IslandData;
import com.example.skyblocks.island.IslandPaintingBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.example.skyblocks.island.SkyIslandBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.client.Minecraft;

/** Draws a placed Sky Island: the scanned island as a miniature floating (and gently bobbing) above its pedestal. */
public class SkyIslandRenderer implements BlockEntityRenderer<SkyIslandBlockEntity> {
    /** Marks an island that couldn't be decoded or baked (retried only if the island changes). */
    private record Broken(IslandData source) {}

    /** Mobs inside are drawn up to this many blocks away per block of their size (per block the island is shown wide). */
    private static final double FIGURE_REACH = 24.0;
    /** Block entities inside (chests, signs, sky blocks...) are drawn up to this far (per block the island is shown wide). */
    private static final double DETAIL_REACH = 48.0;
    /** How high above the bottom of the block the island's lowest point floats. */
    private static final float FLOAT = 0.35F;

    public SkyIslandRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SkyIslandBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        IslandData data = be.island();
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        boolean painting = state.getBlock() instanceof IslandPaintingBlock;
        int width = 1, height = 1;
        if (painting && level != null) {
            if (state.getValue(IslandPaintingBlock.CCW) || state.getValue(IslandPaintingBlock.DOWN)) {
                // part of a joined painting: its bottom corner block draws the whole thing
                if (be.clientView instanceof IslandView old) {
                    old.close();
                }
                be.clientView = null;
                return;
            }
            width = IslandPaintingBlock.width(level, be.getBlockPos(), state);
            height = IslandPaintingBlock.height(level, be.getBlockPos(), state);
        }
        if (data == null || level == null) {
            if (be.clientView instanceof IslandView old) {
                old.close(); // island taken out of a painting
                be.clientView = null;
            }
            if (painting && level != null) {
                // an empty painting still shows its room
                IslandWindow.render(null, null, be.background(), be.rotation(), state.getValue(IslandPaintingBlock.FACING), width, height, poseStack, partialTick, packedLight, 0, 0, false);
            }
            return;
        }
        if (be.clientView instanceof Broken broken) {
            if (broken.source.equals(data)) {
                if (painting) {
                    IslandWindow.render(null, null, be.background(), be.rotation(), state.getValue(IslandPaintingBlock.FACING), width, height, poseStack, partialTick, packedLight, 0, 0, false);
                }
                return; // same island that failed before: don't retry every frame
            }
            be.clientView = null;
        }
        IslandView view = be.clientView instanceof IslandView v && data.equals(v.source) ? v : null;
        if (view == null) {
            if (be.clientView instanceof IslandView old) {
                old.close();
            }
            try {
                view = IslandView.decode(data, level, be.getBlockPos());
            } catch (RuntimeException e) {
                view = null;
            }
            be.clientView = view != null ? view : new Broken(data);
            if (view == null) {
                return;
            }
        }
        IslandMesh mesh;
        try {
            mesh = view.mesh();
        } catch (RuntimeException e) {
            view.close();
            be.clientView = new Broken(data);
            return;
        }

        long time = level.getGameTime();
        // Far away the mobs and block entities inside are a pixel or two: not worth ticking or drawing them.
        // (Distances scale with how big the island is shown: a joined painting or a wide pedestal shows it bigger.)
        double distance = Math.sqrt(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos())));
        double shown = painting ? Math.min(width, height) : Math.max(1.0, be.displayWidth());
        double figureReach = FIGURE_REACH * shown;
        boolean details = distance < DETAIL_REACH * shown;
        // A pedestal's mobs live in its block entity (they can walk over to Sky Islands next to it); a painting's
        // figures are the scanned ones.
        java.util.List<com.example.skyblocks.island.IslandMob> mobs = painting ? null : be.mobs();
        if (painting ? distance < figureReach * view.biggestFigure()
                : distance < figureReach * view.biggestPedestalFigure(mobs)) {
            if (painting) {
                view.tickTo(time);
            } else {
                view.tickPedestal(time, mobs);
            }
        }

        if (painting) {
            IslandWindow.render(view, mesh, be.background(), be.rotation(), state.getValue(IslandPaintingBlock.FACING), width, height, poseStack, partialTick, packedLight,
                    distance, figureReach, details);
            if (be.name() != null) {
                IslandPlaque.renderOnPainting(be.name(), state.getValue(IslandPaintingBlock.FRAME), state.getValue(IslandPaintingBlock.FACING), width, poseStack, buffer, packedLight);
            }
            return;
        }
        poseStack.pushPose();
        // Same block size for every island (like the painting): a 25-wide island fills the pedestal.
        float scale = be.displayWidth() / (float) com.example.skyblocks.island.IslandScan.MAX_SIZE;
        float bob = Mth.sin(((time % 24000L) + partialTick) * 0.05F) * 0.04F;
        poseStack.translate(0.5F, FLOAT + bob, 0.5F);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-view.contentCenterX(), -view.lowestLayer(), -view.contentCenterZ());
        mesh.render(poseStack.last(), buffer);
        // With a shader pack, cats drawn with Shadered's own shader into Iris's buffers don't show: collect them and
        // draw them through our own target (client/iris/IrisIslandTarget); in Iris's shadow pass they are skipped.
        boolean irisCats = details && (com.example.skyblocks.client.iris.IrisIslandTarget.active()
                || com.example.skyblocks.client.iris.IrisIslandTarget.shadowPass());
        if (details) {
            IslandRendering.collectCats = irisCats;
            try {
                view.renderBlockEntities(poseStack, buffer, partialTick);
            } finally {
                IslandRendering.collectCats = false;
            }
            if (irisCats) {
                com.example.skyblocks.client.iris.IrisIslandTarget.queueCats(); // drawn with all the others later
            }
        }
        view.renderPedestal(mobs, poseStack, buffer, partialTick, distance, figureReach);
        poseStack.popPose();
        // its name floats above it while you look at it
        if (be.name() != null && Minecraft.getInstance().hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(be.getBlockPos())) {
            float top = FLOAT + bob + (view.highestLayer() - view.lowestLayer() + 1) * scale;
            IslandPlaque.renderAbovePedestal(be.name(), top, poseStack, buffer, packedLight);
        }
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
