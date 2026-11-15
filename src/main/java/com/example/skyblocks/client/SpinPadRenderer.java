package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SpinPadBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * A spin pad (SpinPadBlockEntity): its plate turning with the cat on it, and once it's going, a vortex of sky whirling
 * up round the cat - three ribbons of the pad's sky (SkyWindow) spiralling up and out, taller and wider the faster it
 * spins - with a ring of light round the plate.
 */
public class SpinPadRenderer implements BlockEntityRenderer<SpinPadBlockEntity> {
    private static final ResourceLocation PLATE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/block/spin_pad_plate.png");
    private static final int STEPS = 36;
    private final SkyWindow.Quads quads = new SkyWindow.Quads();

    public SpinPadRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SpinPadBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float turn = be.turn(partialTick);
        float s = be.clientSpeed;
        // the plate, turning
        pose.pushPose();
        pose.translate(0.5F, 4.02F / 16.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-turn));
        Matrix4f pm = pose.last().pose();
        VertexConsumer plate = buffers.getBuffer(RenderType.entityCutout(PLATE));
        float r = 7.0F / 16.0F;
        plateVertex(plate, pose, -r, -r, 0, 0, light);
        plateVertex(plate, pose, -r, r, 0, 1, light);
        plateVertex(plate, pose, r, r, 1, 1, light);
        plateVertex(plate, pose, r, -r, 1, 0, light);
        pose.popPose();
        if (s < 0.03F) {
            return;
        }
        // the vortex: three ribbons of sky spiralling up and out
        RenderType sky = SkyWindow.type(be.sky(), be.pass());
        Matrix4f m = pose.last().pose();
        float height = 0.7F + 4.8F * s, spread = 1.6F * s;
        float spin = (float) Math.toRadians(turn) * 0.6F;
        quads.clear();
        float[] px = new float[STEPS + 1], py = new float[STEPS + 1], pz = new float[STEPS + 1], w = new float[STEPS + 1];
        for (int k = 0; k < 3; k++) {
            for (int i = 0; i <= STEPS; i++) {
                float f = i / (float) STEPS;
                float rad = 0.45F + spread * (float) Math.pow(f, 1.6);
                float a = spin + k * Mth.TWO_PI / 3 + f * Mth.TWO_PI * 1.25F;
                px[i] = 0.5F + Mth.cos(a) * rad;
                py[i] = 0.3F + f * height;
                pz[i] = 0.5F + Mth.sin(a) * rad;
                w[i] = (0.05F + 0.13F * s) * (float) Math.pow(Mth.sin(Mth.PI * Math.min(1.0F, f * 1.15F)), 0.6) + 0.004F;
            }
            for (int i = 0; i < STEPS; i++) {
                quads.add(px[i], py[i] - w[i], pz[i], px[i + 1], py[i + 1] - w[i + 1], pz[i + 1], px[i + 1], py[i + 1] + w[i + 1], pz[i + 1],
                        px[i], py[i] + w[i], pz[i]);
            }
        }
        if (sky != null) {
            SkyWindow.draw(buffers, sky, m, quads.data, quads.count);
        }
        // a ring of light round the plate
        VertexConsumer g = buffers.getBuffer(GlowTypes.GLOW);
        float a = 0.35F * s;
        for (int i = 0; i < 24; i++) {
            float a0 = i * Mth.TWO_PI / 24, a1 = (i + 1) * Mth.TWO_PI / 24;
            float r0 = 0.47F, r1 = 0.47F + 0.25F * s;
            float y = 4.05F / 16.0F;
            g.addVertex(m, 0.5F + Mth.cos(a0) * r0, y, 0.5F + Mth.sin(a0) * r0).setColor(0.7F, 0.85F, 1.0F, a);
            g.addVertex(m, 0.5F + Mth.cos(a0) * r1, y, 0.5F + Mth.sin(a0) * r1).setColor(0.7F, 0.85F, 1.0F, 0.0F);
            g.addVertex(m, 0.5F + Mth.cos(a1) * r1, y, 0.5F + Mth.sin(a1) * r1).setColor(0.7F, 0.85F, 1.0F, 0.0F);
            g.addVertex(m, 0.5F + Mth.cos(a1) * r0, y, 0.5F + Mth.sin(a1) * r0).setColor(0.7F, 0.85F, 1.0F, a);
        }
    }

    private static void plateVertex(VertexConsumer vc, PoseStack pose, float x, float z, float u, float v, int light) {
        vc.addVertex(pose.last(), x, 0, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 1, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(SpinPadBlockEntity be) {
        return be.clientSpeed > 0.03F;
    }

    @Override
    public AABB getRenderBoundingBox(SpinPadBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 2, p.getY(), p.getZ() - 2, p.getX() + 3, p.getY() + 7, p.getZ() + 3);
    }
}
