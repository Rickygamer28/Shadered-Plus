package com.example.skyblocks.client.island;

import com.example.skyblocks.island.IslandData;
import com.example.skyblocks.island.IslandPaintingBlock;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
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

/** Draws a placed Sky Island: the scanned island as a miniature floating (and gently bobbing) above its pedestal. */
public class SkyIslandRenderer implements BlockEntityRenderer<SkyIslandBlockEntity> {
    /** Marks an island that couldn't be decoded or baked (retried only if the island changes). */
    private record Broken(IslandData source) {}

    /** How high above the bottom of the block the island's lowest point floats. */
    private static final float FLOAT = 0.35F;

    public SkyIslandRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SkyIslandBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        IslandData data = be.island();
        Level level = be.getLevel();
        if (data == null || level == null) {
            if (be.clientView instanceof IslandView old) {
                old.close(); // island taken out of a painting
                be.clientView = null;
            }
            return;
        }
        if (be.clientView instanceof Broken broken) {
            if (broken.source.equals(data)) {
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
        view.tickTo(time);

        poseStack.pushPose();
        BlockState state = be.getBlockState();
        if (state.getBlock() instanceof IslandPaintingBlock) {
            placeInPainting(poseStack, view, state.getValue(IslandPaintingBlock.FACING));
        } else {
            float scale = be.displayWidth() / (float) view.size;
            float bob = Mth.sin(((time % 24000L) + partialTick) * 0.05F) * 0.04F;
            poseStack.translate(0.5F, FLOAT + bob, 0.5F);
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-view.size / 2.0F, 0.0F, -view.size / 2.0F);
        }
        mesh.render(poseStack.last(), buffer);
        view.renderBlockEntities(poseStack, buffer, partialTick);
        view.renderFigures(poseStack, buffer, partialTick);
        poseStack.popPose();
    }

    /** Inside an Island Painting's box (14x14 pixels, 7 deep, in front of its backdrop): as big as fits, centred. */
    private static void placeInPainting(PoseStack poseStack, IslandView view, Direction facing) {
        float yRot = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        // work in the north-facing painting's space (box from z = 8/16 to 15/16, open towards -z)
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));
        poseStack.translate(-0.5F, 0.0F, -0.5F);

        int bottom = view.lowestLayer();
        int tall = view.highestLayer() - bottom + 1;
        float maxWide = 13.0F / 16.0F, maxDeep = 6.0F / 16.0F, maxTall = 13.0F / 16.0F;
        float scale = Math.min(Math.min(maxWide, maxDeep) / view.size, maxTall / tall);
        float y = 1.5F / 16.0F + (maxTall - tall * scale) / 2.0F;
        poseStack.translate(0.5F, y, 11.5F / 16.0F);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-view.size / 2.0F, -bottom, -view.size / 2.0F);
    }

    @Override
    public AABB getRenderBoundingBox(SkyIslandBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        if (be.getBlockState().getBlock() instanceof IslandPaintingBlock) {
            return new AABB(pos);
        }
        IslandData data = be.island();
        double half = be.displayWidth() / 2.0 + 0.5;
        double top = FLOAT + 1.0 + (data != null && data.size() > 0 ? be.displayWidth() * (double) data.height() / data.size() : 1.0);
        return new AABB(pos.getX() + 0.5 - half, pos.getY(), pos.getZ() + 0.5 - half,
                pos.getX() + 0.5 + half, pos.getY() + top, pos.getZ() + 0.5 + half);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
