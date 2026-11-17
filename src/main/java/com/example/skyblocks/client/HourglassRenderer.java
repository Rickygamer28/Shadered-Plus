package com.example.skyblocks.client;

import com.example.skyblocks.HourglassBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * An hourglass (HourglassBlockEntity): its glass and its sand, in 3D - a box round them traced through by
 * shaders/core/hourglass (round glass bulbs, the sand in the top one sinking into a funnel as it runs, a thin stream
 * falling through the neck, the heap in the bottom one growing; the sand glowing gold, glinting). Turned over, the
 * glass spins over in its frame (the frame's the block's model). Drawn after the coloured lights (LatePass).
 */
public class HourglassRenderer implements BlockEntityRenderer<HourglassBlockEntity> {
    static ShaderInstance shader;
    private static final float CY = 0.5F, HX = 0.32F, HY = 0.4F;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "hourglass"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType GLASS = create("skyblocks_hourglass", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public HourglassRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(HourglassBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null || be.getLevel() == null) {
            return;
        }
        Matrix4f m = new Matrix4f(pose.last().pose());
        float since = be.getLevel().getGameTime() - be.flipped() + partialTick;
        float flip = since < HourglassBehaviour.FLIP ? Mth.clamp(since / HourglassBehaviour.FLIP, 0.0F, 1.0F) : 1.0F;
        // (turning over: the sand as it was - all fallen - turning with it, so it ends up on top)
        float fallen = since < HourglassBehaviour.FLIP ? 1.0F : be.fallen(partialTick);
        boolean running = be.running() && since >= HourglassBehaviour.FLIP && fallen < 1.0F;
        float angle = since < HourglassBehaviour.FLIP ? (1.0F - (float) (Math.cos(flip * Math.PI) * 0.5 + 0.5)) : 0.0F;
        Runnable draw = () -> ElevatorRenderer.drawNow(Types.GLASS, vc -> box(vc, m, angle, fallen, running));
        if (!LatePass.later(src -> draw.run())) {
            draw.run();
        }
    }

    /** Its numbers: how long a turning over takes (HourglassBlockEntity.FLIP). */
    static final class HourglassBehaviour {
        static final float FLIP = HourglassBlockEntity.FLIP;
    }

    private static void box(VertexConsumer vc, Matrix4f m, float angle, float fallen, boolean running) {
        Vector3f centre = m.transformPosition(0.5F, CY, 0.5F, new Vector3f());
        float ex = -centre.x, ey = -centre.y, ez = -centre.z;
        int f16 = Math.round(Mth.clamp(fallen, 0.0F, 1.0F) * 65535.0F);
        int r = Math.round(angle * 255.0F), g = f16 >> 8, b = f16 & 255, a = running ? 255 : 0;
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        // (turned over, the glass sweeps out a bigger box: its corners reach round)
        float hy = HY, hz = angle > 0.0F ? HY : HX;
        float[] towards = {-ex - HX, ex - HX, -ey - hy, ey - hy, -ez - hz, ez - hz};
        for (int f = 0; f < 6; f++) {
            if (towards[f] > 0) {
                continue;
            }
            for (int c : faces[f]) {
                float x = ((c & 1) * 2 - 1) * HX, y = (((c >> 1) & 1) * 2 - 1) * hy, z = (((c >> 2) & 1) * 2 - 1) * hz;
                vc.addVertex(m, 0.5F + x, CY + y, 0.5F + z).setColor(r, g, b, a).setUv(c, hz);
            }
        }
    }
}
