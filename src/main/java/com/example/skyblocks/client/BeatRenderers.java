package com.example.skyblocks.client;

import com.example.skyblocks.BeatLampBlock;
import com.example.skyblocks.SpeakerBlock;
import com.example.skyblocks.SpeakerBlockEntity;
import com.example.skyblocks.client.tv.TvPlayers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Things that move to the music: a speaker's cone, a beat lamp's light. */
public final class BeatRenderers {
    /** Forced beat, for the render test (no sound there). */
    public static float testBeat = -1;

    static float beat(SpeakerBlockEntity be) {
        if (testBeat >= 0) return testBeat;
        return Math.max(0, TvPlayers.beatNear(be.getBlockPos().getCenter(), 24));
    }

    /** A speaker's cone: a dark ring that pushes out with each beat, a little light in its middle. */
    public static class Speaker implements BlockEntityRenderer<SpeakerBlockEntity> {
        public Speaker(BlockEntityRendererProvider.Context context) {}

        @Override
        public void render(SpeakerBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            BlockState st = be.getBlockState();
            if (!(st.getBlock() instanceof SpeakerBlock)) return;
            float b = beat(be);
            pose.pushPose();
            pose.translate(0.5F, 0.0F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(-(st.getValue(SpeakerBlock.FACING).toYRot() + 180.0F)));
            Matrix4f m = pose.last().pose();
            // (its front at z = -5/16 in this frame, facing -z; the big cone low, the tweeter high)
            VertexConsumer v = buffers.getBuffer(RenderType.textBackground());
            cone(v, m, 0.0F, 0.33F, 0.19F, -5.0F / 16 - 0.002F - b * 0.035F, b, light);
            cone(v, m, 0.0F, 0.74F, 0.08F, -5.0F / 16 - 0.002F - b * 0.015F, b, light);
            if (b > 0.02F) {
                VertexConsumer g = MachineRenderers.glows(buffers);
                Vector3f[] cam = MachineRenderers.camera();
                MachineRenderers.sprite(g, m, cam[0], cam[1], 0.0F, 0.33F, -5.0F / 16 - 0.05F, 0.1F + b * 0.08F, 0.3F, 0.7F, 1.0F, b * 0.8F);
            }
            pose.popPose();
        }

        /** A cone: rings from its rim in to its middle, pushed out by the beat (both sides drawn). */
        private static void cone(VertexConsumer v, Matrix4f m, float cx, float cy, float r, float z, float b, int light) {
            int seg = 16;
            float[] ring = {1.0F, 0.82F, 0.45F, 0.18F, 0.0F};
            int[] col = {0xFF1A1B20, 0xFF2A2C33, 0xFF22242A, 0xFF3A3D46, 0xFF4A4E58};
            for (int k = 0; k < ring.length - 1; k++) {
                float r0 = r * ring[k], r1 = r * ring[k + 1], z0 = z - b * 0.02F * k, z1 = z - b * 0.02F * (k + 1);
                for (int i = 0; i < seg; i++) {
                    float a0 = i * Mth.TWO_PI / seg, a1 = (i + 1) * Mth.TWO_PI / seg;
                    float[][] q = {{cx + Mth.cos(a0) * r0, cy + Mth.sin(a0) * r0, z0, col[k]}, {cx + Mth.cos(a0) * r1, cy + Mth.sin(a0) * r1, z1, col[k + 1]},
                            {cx + Mth.cos(a1) * r1, cy + Mth.sin(a1) * r1, z1, col[k + 1]}, {cx + Mth.cos(a1) * r0, cy + Mth.sin(a1) * r0, z0, col[k]}};
                    for (int j = 0; j < 4; j++) v.addVertex(m, q[j][0], q[j][1], q[j][2]).setColor((int) q[j][3]).setLight(light);
                    for (int j = 3; j >= 0; j--) v.addVertex(m, q[j][0], q[j][1], q[j][2]).setColor((int) q[j][3]).setLight(light);
                }
            }
        }
    }

    /** A beat lamp's light: a soft glow in its glass, flaring with each beat. */
    public static class Lamp implements BlockEntityRenderer<SpeakerBlockEntity> {
        public Lamp(BlockEntityRendererProvider.Context context) {}

        @Override
        public void render(SpeakerBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            BlockState st = be.getBlockState();
            if (!(st.getBlock() instanceof BeatLampBlock)) return;
            int c = BeatLampBlock.COLOURS[st.getValue(BeatLampBlock.COLOUR)];
            float r = (c >> 16 & 255) / 255.0F, g = (c >> 8 & 255) / 255.0F, bl = (c & 255) / 255.0F;
            float b = beat(be);
            float t = (be.getLevel() == null ? 0 : be.getLevel().getGameTime() % 24000 + pt) / 20.0F;
            float idle = 0.25F + 0.05F * Mth.sin(t * 2.0F + be.getBlockPos().hashCode());
            float a = Math.max(idle, b);
            Matrix4f m = pose.last().pose();
            Vector3f[] cam = MachineRenderers.camera();
            VertexConsumer v = MachineRenderers.glows(buffers);
            MachineRenderers.glow(v, m, cam[0], cam[1], 0.5F, 0.5F, 0.5F, 0.32F + a * 0.35F, r, g, bl, a);
            if (b > 0.3F) {
                MachineRenderers.sprite(v, m, cam[0], cam[1], 0.5F, 0.5F, 0.5F, 1.2F + b * 1.4F, r, g, bl, (b - 0.3F) * 0.35F); // (the flare)
            }
        }
    }

    private BeatRenderers() {}
}
