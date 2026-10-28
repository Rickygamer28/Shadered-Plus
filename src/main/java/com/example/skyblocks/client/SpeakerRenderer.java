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
    /** The light's colour this frame (the speaker's own, or the RGB rainbow). */
    private static float cr = 1.0F, cg = 0.3F, cb = 0.8F;

    public SpeakerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SpeakerBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getBlock() instanceof SpeakerBlock ? be.getBlockState().getValue(SpeakerBlock.FACING) : Direction.NORTH;
        MusicReactor.tick();
        float bass = MusicReactor.bass();
        // a little spring: the cone jumps out on a hit and settles back
        float[] push = PUSH.computeIfAbsent(be, b -> new float[] {0.0F, 0.0F, System.nanoTime() / 1.0e9F, (b.getBlockPos().hashCode() & 255) / 255.0F});
        float now = System.nanoTime() / 1.0e9F;
        float dt = Math.min(0.05F, Math.max(0.0F, now - push[2]));
        push[2] = now;
        float target = bass * bass * 0.07F;
        push[1] += ((target - push[0]) * 900.0F - push[1] * 30.0F) * dt;
        push[0] += push[1] * dt;
        float out = Math.max(-0.02F, Math.min(0.09F, push[0]));
        if (be.rgb()) {
            push[3] = (push[3] + dt * (0.08F + bass * 0.9F)) % 1.0F; // (round the rainbow, faster on the beat)
            float h = push[3] * 6.0F;
            cr = Math.max(0.0F, Math.min(1.0F, Math.abs(h - 3.0F) - 1.0F));
            cg = Math.max(0.0F, Math.min(1.0F, 2.0F - Math.abs(h - 2.0F)));
            cb = Math.max(0.0F, Math.min(1.0F, 2.0F - Math.abs(h - 4.0F)));
        } else {
            int c = be.colour();
            cr = ((c >> 16) & 255) / 255.0F;
            cg = ((c >> 8) & 255) / 255.0F;
            cb = (c & 255) / 255.0F;
        }

        if (be.getLevel() != null) { // (the light in front of it: inside a solid block there's none)
            light = net.minecraft.client.renderer.LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(facing));
        }
        int block = LightTexture.block(light), sky = LightTexture.sky(light);
        float lit = 0.18F + 0.82F * Math.max(block, sky) / 15.0F;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180.0F)); // (front = -z)
        poseStack.translate(0.0F, 0.0F, -0.5F);
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer solid = buffers.getBuffer(DiscoBallRenderer.Types.SOLID_TWO_SIDED);
        // woofer (lower) and tweeter (upper), on the front panel (half a pixel in from the front)
        cone(solid, pose, 0.0F, -0.1F, 0.26F, 0.1F, out, lit, bass);
        cone(solid, pose, 0.0F, 0.35F, 0.075F, 0.03F, out * 0.35F, lit, bass);
        poseStack.popPose();
    }

    /**
     * A speaker cone on the front panel: a raised rubber surround (radius r0), the cone shaded darker towards the middle
     * (so it reads as dished), and the dust cap (radius r1) bulging out in the middle, glowing with the bass. Everything
     * moves out by `out` on a hit.
     */
    private static void cone(VertexConsumer v, Matrix4f pose, float cx, float cy, float r0, float r1, float out, float lit, float bass) {
        float panel = 0.5F / 16.0F;                 // the front panel's depth
        float zSurround = panel - 0.012F, zEdge = panel - 0.004F - out * 0.5F, zInner = panel - 0.006F - out, zCap = panel - 0.035F - out * 1.1F;
        float rs = r0 * 1.14F;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (Math.PI * 2 * i / SEGMENTS), a1 = (float) (Math.PI * 2 * (i + 1) / SEGMENTS);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            // metal mounting ring, then the rubber surround (lighter on top: light from above)
            float rm = r0 * 1.24F;
            float metal = (0.45F + 0.12F * s0) * lit;
            quad(v, pose, cx + c0 * rm, cy + s0 * rm, panel - 0.002F, cx + c1 * rm, cy + s1 * rm, panel - 0.002F,
                    cx + c1 * rs, cy + s1 * rs, panel - 0.004F, cx + c0 * rs, cy + s0 * rs, panel - 0.004F, metal, metal, metal * 1.04F);
            float rubber = (0.07F + 0.05F * (0.5F + 0.5F * s0)) * lit;
            quad(v, pose, cx + c0 * rs, cy + s0 * rs, panel - 0.004F, cx + c1 * rs, cy + s1 * rs, panel - 0.004F,
                    cx + c1 * r0, cy + s1 * r0, zSurround - out * 0.5F, cx + c0 * r0, cy + s0 * r0, zSurround - out * 0.5F, rubber, rubber, rubber);
            // the cone: lighter at the rim, darker towards the middle, faint ribs
            float rib = (i % 2 == 0) ? 1.0F : 0.92F;
            float rim = 0.2F * rib * lit, mid = 0.1F * rib * lit;
            v.vertex(pose, cx + c0 * r0, cy + s0 * r0, zEdge).color(rim, rim, rim * 1.05F, 1.0F).endVertex();
            v.vertex(pose, cx + c1 * r0, cy + s1 * r0, zEdge).color(rim, rim, rim * 1.05F, 1.0F).endVertex();
            v.vertex(pose, cx + c1 * r1, cy + s1 * r1, zInner).color(mid, mid, mid * 1.05F, 1.0F).endVertex();
            v.vertex(pose, cx + c0 * r1, cy + s0 * r1, zInner).color(mid, mid, mid * 1.05F, 1.0F).endVertex();
            // the dust cap: a little dome, glowing pink on the beat
            float glow = Math.min(1.0F, 0.1F * lit + bass * 0.9F);
            float hi = Math.min(1.0F, glow + 0.08F * lit);
            v.vertex(pose, cx + c0 * r1, cy + s0 * r1, zInner).color(glow * cr, glow * cg, glow * cb, 1.0F).endVertex();
            v.vertex(pose, cx + c1 * r1, cy + s1 * r1, zInner).color(glow * cr, glow * cg, glow * cb, 1.0F).endVertex();
            v.vertex(pose, cx + c1 * r1 * 0.5F, cy + s1 * r1 * 0.5F, (zInner + zCap) * 0.5F - 0.004F).color(hi * cr, hi * cg, hi * cb, 1.0F).endVertex();
            v.vertex(pose, cx + c0 * r1 * 0.5F, cy + s0 * r1 * 0.5F, (zInner + zCap) * 0.5F - 0.004F).color(hi * cr, hi * cg, hi * cb, 1.0F).endVertex();
            v.vertex(pose, cx + c0 * r1 * 0.5F, cy + s0 * r1 * 0.5F, (zInner + zCap) * 0.5F - 0.004F).color(hi * cr, hi * cg, hi * cb, 1.0F).endVertex();
            v.vertex(pose, cx + c1 * r1 * 0.5F, cy + s1 * r1 * 0.5F, (zInner + zCap) * 0.5F - 0.004F).color(hi * cr, hi * cg, hi * cb, 1.0F).endVertex();
            v.vertex(pose, cx, cy, zCap).color(Math.min(1.0F, hi * cr + 0.05F), Math.min(1.0F, hi * cg + 0.05F), Math.min(1.0F, hi * cb + 0.05F), 1.0F).endVertex();
            v.vertex(pose, cx, cy, zCap).color(Math.min(1.0F, hi * cr + 0.05F), Math.min(1.0F, hi * cg + 0.05F), Math.min(1.0F, hi * cb + 0.05F), 1.0F).endVertex();
        }
    }

    private static void quad(VertexConsumer v, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, float r, float g, float b) {
        v.vertex(pose, x0, y0, z0).color(r, g, b, 1.0F).endVertex();
        v.vertex(pose, x1, y1, z1).color(r, g, b, 1.0F).endVertex();
        v.vertex(pose, x2, y2, z2).color(r, g, b, 1.0F).endVertex();
        v.vertex(pose, x3, y3, z3).color(r, g, b, 1.0F).endVertex();
    }
}
