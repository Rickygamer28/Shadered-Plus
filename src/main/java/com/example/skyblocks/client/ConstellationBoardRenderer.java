package com.example.skyblocks.client;

import com.example.skyblocks.ConstellationBoardBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * The Constellation Board (shaders/core/constellation): the night across its face inside the frame (in the world's
 * position, so boards side by side are one night), then the lines between its stars, then the stars.
 */
public class ConstellationBoardRenderer implements BlockEntityRenderer<ConstellationBoardBlock.Entity> {
    private static final float IN0 = 1.0F / 16.0F, IN = 14.0F / 16.0F, LIFT = 1.0F / 16.0F + 0.004F;

    public ConstellationBoardRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ConstellationBoardBlock.Entity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (FxShaders.constellation == null || !be.getBlockState().hasProperty(ConstellationBoardBlock.FACING)) {
            return;
        }
        Direction f = be.getBlockState().getValue(ConstellationBoardBlock.FACING);
        Direction right = f.getCounterClockWise();
        // (the face: from the back of the block, LIFT out)
        float bx = -f.getStepX() * (0.5F - LIFT) + 0.5F, bz = -f.getStepZ() * (0.5F - LIFT) + 0.5F;
        float rx = right.getStepX(), rz = right.getStepZ();
        float fx = f.getStepX() * 0.002F, fz = f.getStepZ() * 0.002F;
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buffers.getBuffer(FxShaders.Types.CONSTELLATION);
        float wu = (be.getBlockPos().getX() + 0.5F) * rx + (be.getBlockPos().getZ() + 0.5F) * rz;
        float wy = be.getBlockPos().getY();
        // ---- the night ----
        float[][] corners = {{IN0, IN0}, {IN0 + IN, IN0}, {IN0 + IN, IN0 + IN}, {IN0, IN0 + IN}};
        for (float[] c : corners) {
            float u = c[0] - 0.5F;
            vc.addVertex(m, bx + rx * u, c[1], bz + rz * u).setUv(wu + u, wy + c[1]).setColor(0, 0, 0, 255);
        }
        // ---- the lines ----
        for (int[] l : be.lines) {
            float[] a = spot(l[0]), b = spot(l[1]);
            float du = b[0] - a[0], dv = b[1] - a[1], len = (float) Math.sqrt(du * du + dv * dv);
            if (len < 1.0E-4F) {
                continue;
            }
            float w = 0.022F, nu = -dv / len * w, nv = du / len * w;
            int seed = (l[0] * 7 + l[1] * 13) & 255;
            point(vc, m, bx + fx, bz + fz, rx, rz, a[0] + nu, a[1] + nv, 0, 1, seed);
            point(vc, m, bx + fx, bz + fz, rx, rz, a[0] - nu, a[1] - nv, 0, -1, seed);
            point(vc, m, bx + fx, bz + fz, rx, rz, b[0] - nu, b[1] - nv, len, -1, seed);
            point(vc, m, bx + fx, bz + fz, rx, rz, b[0] + nu, b[1] + nv, len, 1, seed);
        }
        // ---- the stars ----
        for (int i = 0; i < ConstellationBoardBlock.GRID * ConstellationBoardBlock.GRID; i++) {
            if (!be.star(i)) {
                continue;
            }
            float[] p = spot(i);
            float s = 0.11F;
            int seed = (i * 37 + (int) be.getBlockPos().asLong()) & 255;
            int g = i == be.picked ? 255 : 160;
            float ox = bx + fx * 2, oz = bz + fz * 2;
            star(vc, m, ox, oz, rx, rz, p[0] - s, p[1] - s, -1, -1, g, seed);
            star(vc, m, ox, oz, rx, rz, p[0] + s, p[1] - s, 1, -1, g, seed);
            star(vc, m, ox, oz, rx, rz, p[0] + s, p[1] + s, 1, 1, g, seed);
            star(vc, m, ox, oz, rx, rz, p[0] - s, p[1] + s, -1, 1, g, seed);
        }
    }

    /** A spot's place on the board: across from the middle, and up from the bottom. */
    private static float[] spot(int i) {
        int g = ConstellationBoardBlock.GRID;
        return new float[] {IN0 + IN * ((i % g) + 0.5F) / g - 0.5F, IN0 + IN * ((i / g) + 0.5F) / g};
    }

    private static void point(VertexConsumer vc, Matrix4f m, float bx, float bz, float rx, float rz, float u, float v, float along, float across, int seed) {
        vc.addVertex(m, bx + rx * u, v, bz + rz * u).setUv(along * 4.0F, across).setColor(2, 0, seed, 255);
    }

    private static void star(VertexConsumer vc, Matrix4f m, float bx, float bz, float rx, float rz, float u, float v, float qu, float qv, int g, int seed) {
        vc.addVertex(m, bx + rx * u, v, bz + rz * u).setUv(qu, qv).setColor(1, g, seed, 255);
    }
}
