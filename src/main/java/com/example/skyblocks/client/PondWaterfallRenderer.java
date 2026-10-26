package com.example.skyblocks.client;

import com.example.skyblocks.PondWaterfallBlock;
import com.example.skyblocks.PondWaterfallBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * A pond waterfall's water: a sheet pouring off the ledge near the rocks' top, curving out and down into the water
 * in front (the game's flowing water, tinted for the biome as any water; its edges thinner and a little ragged,
 * rippling as it falls), and the foam where it lands (client/PondLife).
 */
public class PondWaterfallRenderer implements BlockEntityRenderer<PondWaterfallBlockEntity> {
    private static final ResourceLocation FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");
    private static final int SEGMENTS = 10;

    public PondWaterfallRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(PondWaterfallBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 1, p.getY() - 4, p.getZ() - 1, p.getX() + 2, p.getY() + 2, p.getZ() + 2);
    }

    @Override
    public void render(PondWaterfallBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !(be.getBlockState().getBlock() instanceof PondWaterfallBlock)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        Direction facing = be.getBlockState().getValue(PondWaterfallBlock.FACING);
        double land = PondWaterfallBlock.landing(be.getLevel(), pos, facing) - pos.getY();
        int tint = 0xFF000000 | BiomeColors.getAverageWaterColor(be.getLevel(), pos);
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FLOW);
        float t = LavaLampRenderer.seconds();
        // (worked out facing north - pouring to -z - then turned round the block's middle)
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180.0F));
        poseStack.translate(-0.5, 0.0, -0.5);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vc = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        float top = 21.0F / 16.0F, start = 1.5F / 16.0F; // (off the end of the ledge, out over the front rocks)
        float drop = (float) (top - land), out = 0.36F + 0.06F * Math.min(drop, 2.0F); // (clear of the pond's rim)
        float u0 = sp.getU(0.3F), u1 = sp.getU(0.7F);
        // (first along the top of the ledge, flowing out to its end)
        float[] l0 = {top + 0.008F, 8.5F / 16.0F, 0.0F, 1.0F, 0.0F}, l1 = {top + 0.008F, start, 0.0F, 1.0F, 0.0F};
        quadVertex(vc, pose, 0.5F + 0.17F, l0, u1, sp.getV(0.0F), tint, 190, light, 1.0F, 0.0F);
        quadVertex(vc, pose, 0.5F + 0.17F, l1, u1, sp.getV(0.5F), tint, 190, light, 1.0F, 0.0F);
        quadVertex(vc, pose, 0.5F - 0.17F, l1, u0, sp.getV(0.5F), tint, 190, light, 1.0F, 0.0F);
        quadVertex(vc, pose, 0.5F - 0.17F, l0, u0, sp.getV(0.0F), tint, 190, light, 1.0F, 0.0F);
        for (int k = 0; k < SEGMENTS; k++) {
            float s0 = k / (float) SEGMENTS, s1 = (k + 1) / (float) SEGMENTS;
            float[] a = path(s0, top, drop, start, out, t), b = path(s1, top, drop, start, out, t);
            // (narrower at the lip, spreading as it falls; the halves of the sprite one after another)
            float w0 = 0.19F + 0.07F * s0, w1 = 0.19F + 0.07F * s1;
            float v0 = sp.getV((k % 2) * 0.5F), v1 = sp.getV((k % 2) * 0.5F + 0.5F);
            int alpha0 = (int) (205 + 40 * s0), alpha1 = (int) (205 + 40 * s1);
            for (int side = 0; side < 2; side++) { // (both faces)
                float n = side == 0 ? 1.0F : -1.0F;
                quadVertex(vc, pose, 0.5F - w0 * n, a, u0, v0, tint, alpha0, light, a[3], a[4]);
                quadVertex(vc, pose, 0.5F + w0 * n, a, u1, v0, tint, alpha0, light, a[3], a[4]);
                quadVertex(vc, pose, 0.5F + w1 * n, b, u1, v1, tint, alpha1, light, b[3], b[4]);
                quadVertex(vc, pose, 0.5F - w1 * n, b, u0, v1, tint, alpha1, light, b[3], b[4]);
            }
        }
        poseStack.popPose();
        // the foam where it lands (drawn after everything, over the pond's water)
        double reach = 0.5 - start + out + 0.04;
        double fx = pos.getX() + 0.5 + facing.getStepX() * reach, fz = pos.getZ() + 0.5 + facing.getStepZ() * reach;
        PondLife.sawWaterfall(fx, pos.getY() + land, fz, facing);
    }

    /** Along the falling water (s: 0 at the lip .. 1 where it lands): {y, z, ripple across, normal y, normal z}. */
    private static float[] path(float s, float top, float drop, float start, float out, float t) {
        float y = top - drop * s * s + 0.02F * (1.0F - s);
        float z = start - out * (float) Math.sqrt(s) - 0.04F;
        float ripple = 0.012F * Mth.sin(t * 9.0F - s * 14.0F) * s; // (wobbling as it falls)
        // (its slope: for its shading)
        float dy = -2.0F * drop * s, dz = -out * 0.5F / (float) Math.sqrt(Math.max(s, 0.02F));
        float l = (float) Math.sqrt(dy * dy + dz * dz);
        return new float[] {y, z + ripple, ripple, -dz / l, dy / l};
    }

    private static void quadVertex(VertexConsumer vc, PoseStack.Pose pose, float x, float[] p, float u, float v, int tint, int alpha,
                                   int light, float ny, float nz) {
        vc.addVertex(pose, x, p[0], p[1]).setColor((tint >> 16) & 255, (tint >> 8) & 255, tint & 255, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen(PondWaterfallBlockEntity be) {
        return true;
    }
}
