package com.example.skyblocks.client;

import com.example.skyblocks.SpeakerBlock;
import com.example.skyblocks.SpeakerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * The speaker's cone (and tweeter) on its front: pushed out with the bass, springing back, the dust cap and a ring of
 * light glowing on the beat.
 */
public class SpeakerRenderer implements BlockEntityRenderer<SpeakerBlockEntity> {
    private static final int SEGMENTS = 24;
    private static final Map<SpeakerBlockEntity, float[]> PUSH = new WeakHashMap<>();

    public SpeakerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SpeakerBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getBlock() instanceof SpeakerBlock ? be.getBlockState().getValue(SpeakerBlock.FACING) : Direction.NORTH;
        MusicReactor.tick();
        float bass = MusicReactor.bass();
        // a little spring: the cone jumps out on a hit and settles back
        float[] push = PUSH.computeIfAbsent(be, b -> new float[] {0.0F, 0.0F, System.nanoTime() / 1.0e9F});
        float now = System.nanoTime() / 1.0e9F;
        float dt = Math.min(0.05F, Math.max(0.0F, now - push[2]));
        push[2] = now;
        float target = bass * bass * 0.07F;
        push[1] += ((target - push[0]) * 900.0F - push[1] * 30.0F) * dt;
        push[0] += push[1] * dt;
        float out = Math.max(-0.02F, Math.min(0.09F, push[0]));

        int block = LightTexture.block(light), sky = LightTexture.sky(light);
        float lit = 0.18F + 0.82F * Math.max(block, sky) / 15.0F;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180.0F)); // (front = -z)
        poseStack.translate(0.0F, 0.0F, -0.5F);
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer solid = buffers.getBuffer(DiscoBallRenderer.Types.BALL);
        // woofer (lower) and tweeter (upper)
        cone(solid, pose, 0.0F, -0.1F, 0.3F, 0.09F, 0.16F, out, lit, bass);
        cone(solid, pose, 0.0F, 0.32F, 0.09F, 0.03F, 0.05F, out * 0.3F, lit, bass);
        poseStack.popPose();
    }

    /** A cone: rim radius r0 at the front face, narrowing to r1 at depth, pushed out by `out`, dust cap in the middle. */
    private static void cone(VertexConsumer v, Matrix4f pose, float cx, float cy, float r0, float r1, float depth, float out, float lit,
                             float bass) {
        float zRim = -0.002F, zIn = depth - out, zCap = depth - out * 1.1F - 0.03F;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (Math.PI * 2 * i / SEGMENTS), a1 = (float) (Math.PI * 2 * (i + 1) / SEGMENTS);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            // the surround (a dark rubber ring), then the cone, ribbed
            float rs = r0 * 1.12F;
            float ring = 0.09F * lit;
            quad(v, pose, cx + c0 * rs, cy + s0 * rs, zRim, cx + c1 * rs, cy + s1 * rs, zRim, cx + c1 * r0, cy + s1 * r0, zRim - out * 0.5F,
                    cx + c0 * r0, cy + s0 * r0, zRim - out * 0.5F, ring, ring, ring * 1.1F);
            float shade = (0.16F + 0.05F * (i % 2)) * lit;
            quad(v, pose, cx + c0 * r0, cy + s0 * r0, zRim - out * 0.5F, cx + c1 * r0, cy + s1 * r0, zRim - out * 0.5F,
                    cx + c1 * r1, cy + s1 * r1, zIn, cx + c0 * r1, cy + s0 * r1, zIn, shade, shade, shade * 1.05F);
            // the dust cap, its glow brighter with the bass
            float glow = Math.min(1.0F, 0.12F * lit + bass * 0.9F);
            quad(v, pose, cx + c0 * r1, cy + s0 * r1, zIn, cx + c1 * r1, cy + s1 * r1, zIn, cx, cy, zCap, cx, cy, zCap,
                    glow, glow * 0.25F, glow * 0.8F);
        }
    }

    private static void quad(VertexConsumer v, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, float r, float g, float b) {
        v.addVertex(pose, x0, y0, z0).setColor(r, g, b, 1.0F);
        v.addVertex(pose, x1, y1, z1).setColor(r, g, b, 1.0F);
        v.addVertex(pose, x2, y2, z2).setColor(r, g, b, 1.0F);
        v.addVertex(pose, x3, y3, z3).setColor(r, g, b, 1.0F);
    }
}
