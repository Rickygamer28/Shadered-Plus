package com.example.skyblocks.client;

import com.example.skyblocks.BeatLampBlock;
import com.example.skyblocks.BeatLampBlockEntity;
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

/** Things that move to a screen's music: a beat lamp's light. */
public final class BeatRenderers {
    /** Forced beat, for the render test (no sound there). */
    public static float testBeat = -1;

    static float beat(BeatLampBlockEntity be) {
        if (testBeat >= 0) return testBeat;
        return Math.max(0, TvPlayers.beatNear(be.getBlockPos().getCenter(), 24));
    }

    /** A beat lamp's light: a soft glow in its glass, flaring with each beat. */
    public static class Lamp implements BlockEntityRenderer<BeatLampBlockEntity> {
        public Lamp(BlockEntityRendererProvider.Context context) {}

        @Override
        public void render(BeatLampBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
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
