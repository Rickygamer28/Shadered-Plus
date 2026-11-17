package com.example.skyblocks.client;

import com.example.skyblocks.ChalkRuneBlock;
import com.example.skyblocks.ChalkRuneBlockEntity;
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
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * A lit magic circle (ChalkRuneBlockEntity, its ring closed): shaders/core/magic_circle - on the ground over its ring,
 * a circle of light in its rune's colour (a double ring with a band of runes turning, a six-pointed star turning the
 * other way, its rune in the middle, pulsing), and over it a faint wall of light with motes rising. Light, added.
 */
public class MagicCircleRenderer implements BlockEntityRenderer<ChalkRuneBlockEntity> {
    static ShaderInstance shader;
    /** Each rune's colour. */
    private static final int[] COLOUR = {0xFFFFFF, 0xC0F2FF, 0xFF7799, 0xFFCC4D, 0x77FF88, 0x99FF4D, 0x8C99FF};
    private static final int AROUND = 48;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "magic_circle"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType CIRCLE = create("skyblocks_magic_circle", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
        // (with a shader pack: its depth written - laid over the pack's image only where it's drawn)
        static final RenderType CIRCLE_DEPTH = create("skyblocks_magic_circle_depth", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public MagicCircleRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ChalkRuneBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int ring = be.ring();
        if (shader == null || ring <= 0) {
            return;
        }
        int rune = be.getBlockState().getValue(ChalkRuneBlock.RUNE);
        int colour = COLOUR[Mth.clamp(rune, 0, 6)];
        ShaderLightRenderer.add(pose, 0.5F, 0.3F, 0.5F, ring + 2.5F, colour);
        Matrix4f m = new Matrix4f(pose.last().pose());
        float reach = ring + 0.5F;
        RenderType type = com.example.skyblocks.client.iris.IrisDefer.drawing() ? Types.CIRCLE_DEPTH : Types.CIRCLE;
        Runnable draw = () -> ElevatorRenderer.drawNow(type, vc -> {
            int r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255;
            // the circle on the ground (mode 0 in its alpha's low bits: rune in the rest)
            float y = 0.04F;
            int a = rune * 2;
            vc.addVertex(m, 0.5F - reach, y, 0.5F - reach).setColor(r, g, b, a).setUv(-1, -1);
            vc.addVertex(m, 0.5F - reach, y, 0.5F + reach).setColor(r, g, b, a).setUv(-1, 1);
            vc.addVertex(m, 0.5F + reach, y, 0.5F + reach).setColor(r, g, b, a).setUv(1, 1);
            vc.addVertex(m, 0.5F + reach, y, 0.5F - reach).setColor(r, g, b, a).setUv(1, -1);
            // the wall of light round it, motes rising (mode 1)
            float h = 2.2F;
            for (int i = 0; i < AROUND; i++) {
                float a0 = i * Mth.TWO_PI / AROUND, a1 = (i + 1) * Mth.TWO_PI / AROUND;
                float u0 = i / (float) AROUND, u1 = (i + 1) / (float) AROUND;
                vc.addVertex(m, 0.5F + Mth.cos(a0) * reach, y, 0.5F + Mth.sin(a0) * reach).setColor(r, g, b, a + 1).setUv(u0 * reach * 6.0F, 0);
                vc.addVertex(m, 0.5F + Mth.cos(a1) * reach, y, 0.5F + Mth.sin(a1) * reach).setColor(r, g, b, a + 1).setUv(u1 * reach * 6.0F, 0);
                vc.addVertex(m, 0.5F + Mth.cos(a1) * reach, y + h, 0.5F + Mth.sin(a1) * reach).setColor(r, g, b, a + 1).setUv(u1 * reach * 6.0F, 1);
                vc.addVertex(m, 0.5F + Mth.cos(a0) * reach, y + h, 0.5F + Mth.sin(a0) * reach).setColor(r, g, b, a + 1).setUv(u0 * reach * 6.0F, 1);
            }
        });
        if (!LatePass.later(src -> draw.run())) {
            draw.run();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ChalkRuneBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(ChalkRuneBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(4.0, 0.0, 4.0).expandTowards(0, 3.0, 0);
    }
}
