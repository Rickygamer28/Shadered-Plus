package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.YarnBallEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/** The yarn ball: a little wound ball, rolling the way it goes. */
public class YarnBallRenderer extends EntityRenderer<YarnBallEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/entity/yarn_ball.png");
    private static final float R = 0.13F;
    private static final int LAT = 8, LON = 12;

    public YarnBallRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.12F;
    }

    @Override
    public void render(YarnBallEntity ball, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, R, 0);
        float roll = Mth.lerp(partialTick, ball.rollO, ball.roll);
        pose.mulPose(new Quaternionf().rotationAxis(roll, ball.axisX, 0, ball.axisZ));
        PoseStack.Pose p = pose.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (int i = 0; i < LAT; i++) {
            float t0 = Mth.PI * i / LAT, t1 = Mth.PI * (i + 1) / LAT;
            for (int j = 0; j < LON; j++) {
                float f0 = Mth.TWO_PI * j / LON, f1 = Mth.TWO_PI * (j + 1) / LON;
                // (drawn both sides: no worry about which way round)
                vertex(vc, p, t0, f0, j / (float) LON, i / (float) LAT, light);
                vertex(vc, p, t1, f0, j / (float) LON, (i + 1) / (float) LAT, light);
                vertex(vc, p, t1, f1, (j + 1) / (float) LON, (i + 1) / (float) LAT, light);
                vertex(vc, p, t0, f1, (j + 1) / (float) LON, i / (float) LAT, light);
            }
        }
        // a loose end of yarn trailing off it
        float x = Mth.sin(1.2F) * R;
        vertex(vc, p, 1.2F, 0, 0, 0.5F, light);
        vc.addVertex(p, x + 0.12F, -R + 0.005F, 0.05F).setColor(-1).setUv(0.1F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
        vc.addVertex(p, x + 0.12F, -R + 0.005F, 0.07F).setColor(-1).setUv(0.1F, 0.55F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
        vertex(vc, p, 1.25F, 0.15F, 0, 0.55F, light);
        pose.popPose();
        super.render(ball, yaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float theta, float phi, float u, float v, int light) {
        float nx = Mth.sin(theta) * Mth.cos(phi), ny = Mth.cos(theta), nz = Mth.sin(theta) * Mth.sin(phi);
        vc.addVertex(p, nx * R, ny * R, nz * R).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(YarnBallEntity ball) {
        return TEXTURE;
    }
}
