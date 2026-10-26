package com.example.skyblocks.client;

import com.example.skyblocks.FxBlock;
import com.example.skyblocks.GlowColourBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The shader effect test blocks (FxBlock), our own takes on looks from other mods:
 * the warp block (shaders/core/warp: layers of a glowing vein net drifting across the screen, like the End portal),
 * the rift (a jagged black crack with red, green and blue copies swinging round it: chromatic aberration), and
 * the static block (shaders/core/tv_static: per-pixel noise, new every frame).
 */
public class FxRenderer implements BlockEntityRenderer<GlowColourBlockEntity> {
    static ShaderInstance warp, tvStatic;
    private static final ResourceLocation VEINS = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/effect/warp_veins.png");

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "warp"), DefaultVertexFormat.POSITION_COLOR), s -> warp = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "tv_static"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> tvStatic = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType WARP = create("skyblocks_warp", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> warp))
                        .setTextureState(new TextureStateShard(VEINS, false, false))
                        .setTexturingState(new TexturingStateShard("skyblocks_warp_time", () -> {
                            if (warp != null) {
                                warp.safeGetUniform("WarpTime").set(LavaLampRenderer.seconds());
                            }
                        }, () -> {}))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));
        static final RenderType STATIC = create("skyblocks_tv_static", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> tvStatic))
                        .setTexturingState(new TexturingStateShard("skyblocks_tv_static_frame", () -> {
                            if (tvStatic != null) {
                                tvStatic.safeGetUniform("Frame").set((float) ((System.nanoTime() / 16_666_667L) % 1_000_000L));
                            }
                        }, () -> {}))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));
        /** The rift's coloured copies: added together (where all three meet: white, covered by the black crack). */
        static final RenderType FRINGE = create("skyblocks_rift_fringe", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 4096,
                false, true, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
        static final RenderType CRACK = create("skyblocks_rift_crack", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 4096,
                false, false, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public FxRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GlowColourBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Block block = be.getBlockState().getBlock();
        int c = be.colour();
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        Matrix4f m = poseStack.last().pose();
        if (block instanceof FxBlock.Warp) {
            if (warp != null) {
                cube(buffers.getBuffer(Types.WARP), m, r, g, b, false);
            }
        } else if (block instanceof FxBlock.Static) {
            if (tvStatic != null) {
                cube(buffers.getBuffer(Types.STATIC), m, r, g, b, true);
            }
        } else if (block instanceof FxBlock.Rift) {
            rift(be, poseStack, buffers, r, g, b);
        }
    }

    /** A full block's six faces (facing out); uv: across each face (+ 2 per face, so each face's noise is its own). */
    private static void cube(VertexConsumer vc, Matrix4f m, int r, int g, int b, boolean uv) {
        float[][] f = {
                {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1}, {1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0},
                {1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1}, {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},
                {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0}, {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1},
        };
        float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < 6; i++) {
            for (int v = 0; v < 4; v++) {
                VertexConsumer vtx = vc.addVertex(m, f[i][v * 3], f[i][v * 3 + 1], f[i][v * 3 + 2]);
                if (uv) {
                    vtx.setUv(uvs[v][0] + 2.0F * i, uvs[v][1]);
                }
                vtx.setColor(r, g, b, 255);
            }
        }
    }

    // ---- the rift ----

    /** Each rift's crack (its triangles, x across / y up, in a plane), made once from where it is. */
    private static final Map<Long, float[]> CRACKS = new HashMap<>();

    private static float[] crack(long seed) {
        return CRACKS.computeIfAbsent(seed, s -> {
            RandomSource rnd = RandomSource.create(s);
            List<Float> tris = new ArrayList<>();
            branch(tris, rnd, 0.0F, -0.42F, 0.0F, 0.42F, 0.06F, 5);
            for (int i = 0; i < 2 + rnd.nextInt(2); i++) {
                float y = -0.25F + 0.5F * rnd.nextFloat();
                float x = 0.03F * (rnd.nextFloat() - 0.5F);
                float ang = (rnd.nextBoolean() ? 1 : -1) * (0.5F + 0.6F * rnd.nextFloat());
                float len = 0.12F + 0.14F * rnd.nextFloat();
                branch(tris, rnd, x, y, x + (float) Math.sin(ang) * len, y + (float) Math.cos(ang) * len * (rnd.nextBoolean() ? 1 : -1), 0.025F, 4);
            }
            float[] out = new float[tris.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = tris.get(i);
            }
            return out;
        });
    }

    /** A jagged line from (x0, y0) to (x1, y1), thick in the middle, tapering to points, as triangles. */
    private static void branch(List<Float> tris, RandomSource rnd, float x0, float y0, float x1, float y1, float width, int depth) {
        int n = 1 << depth;
        float[] px = new float[n + 1], py = new float[n + 1];
        px[0] = x0; py[0] = y0; px[n] = x1; py[n] = y1;
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        float nx = -(y1 - y0) / len, ny = (x1 - x0) / len;
        for (int step = n; step > 1; step /= 2) {
            float amp = len * 0.32F * step / n;
            for (int i = step / 2; i < n; i += step) {
                float d = (rnd.nextFloat() - 0.5F) * amp;
                px[i] = (px[i - step / 2] + px[i + step / 2]) / 2 + nx * d;
                py[i] = (py[i - step / 2] + py[i + step / 2]) / 2 + ny * d;
            }
        }
        for (int i = 0; i < n; i++) {
            float t0 = (float) i / n, t1 = (float) (i + 1) / n;
            float w0 = width * (float) Math.pow(Math.sin(Math.PI * t0), 0.7) * (0.7F + 0.6F * rnd.nextFloat());
            float w1 = width * (float) Math.pow(Math.sin(Math.PI * t1), 0.7) * (0.7F + 0.6F * rnd.nextFloat());
            float dx = px[i + 1] - px[i], dy = py[i + 1] - py[i];
            float l = (float) Math.max(Math.hypot(dx, dy), 1e-5);
            float ox = -dy / l, oy = dx / l;
            float[] q = {px[i] + ox * w0, py[i] + oy * w0, px[i + 1] + ox * w1, py[i + 1] + oy * w1,
                         px[i + 1] - ox * w1, py[i + 1] - oy * w1, px[i] - ox * w0, py[i] - oy * w0};
            int[] order = {0, 1, 2, 0, 2, 3};
            for (int k : order) {
                tris.add(q[k * 2]);
                tris.add(q[k * 2 + 1]);
            }
        }
    }

    private static void rift(GlowColourBlockEntity be, PoseStack poseStack, MultiBufferSource buffers, int tr, int tg, int tb) {
        long seed = be.getBlockPos().asLong() * 0x9E3779B97F4A7C15L;
        float[] tris = crack(seed);
        float t = LavaLampRenderer.seconds();
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees((seed >>> 33) % 360));
        Matrix4f m = poseStack.last().pose();
        // the three colour copies, swinging round the crack (each a third of a turn behind the last), pulsing apart
        boolean tinted = (tr & tg & tb) != 255;
        float orbit = t * (float) Math.PI * 2.0F / 4.0F, swing = t * (float) Math.PI * 2.0F / 2.0F;
        VertexConsumer fringe = buffers.getBuffer(Types.FRINGE);
        for (int ch = 0; ch < 3; ch++) {
            float sep = 0.05F * (float) Math.sin(swing + ch * 2.0943951F);
            float ox = sep * (float) Math.cos(orbit), oy = sep * (float) Math.sin(orbit);
            int r = ch == 0 ? 255 : 0, g = ch == 1 ? 255 : 0, b = ch == 2 ? 255 : 0;
            if (tinted) { // (a dye: the copies in shades of its colour)
                float k = 0.55F + 0.45F * ch / 2.0F;
                r = (int) (tr * k); g = (int) (tg * k); b = (int) (tb * k);
            }
            for (int i = 0; i < tris.length; i += 2) {
                fringe.addVertex(m, tris[i] + ox, tris[i + 1] + oy, 0.0F).setColor(r, g, b, 255);
            }
        }
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(Types.FRINGE); // (first: the black crack goes over where they all meet)
        }
        VertexConsumer core = buffers.getBuffer(Types.CRACK);
        for (int i = 0; i < tris.length; i += 2) {
            core.addVertex(m, tris[i], tris[i + 1], 0.0F).setColor(8, 8, 10, 255);
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(GlowColourBlockEntity be) {
        return false;
    }
}
