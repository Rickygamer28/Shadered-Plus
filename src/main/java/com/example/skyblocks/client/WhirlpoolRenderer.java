package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.WhirlpoolBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The whirlpool singularity: alone, a vortex swirling down in its frame - each fluid it holds a band of its own, flowing
 * round and down the funnel in a spiral (shaders/core/whirlpool), the bands stirred into each other where they meet -
 * rising from the bottom of its frame, wider and taller the fuller it is. In its
 * containment tank: the tank filled with what it holds, in layers by how heavy each is (the lightest - gases - at the
 * top), its surface rippling when something comes in or goes out, the whirlpool swirling down from it in the middle.
 */
public class WhirlpoolRenderer implements BlockEntityRenderer<WhirlpoolBlockEntity> {
    private static final int SEGMENTS = 24, RINGS = 14;
    /** (In a tank: the vortex's shape, scaled up to the tank.) */
    private static final float TANK_RIM = 0.42F, TANK_DEPTH = 0.46F;
    /** The vortex's throat: just over its frame's floor. */
    private static final float BOTTOM = 0.2F;

    /** How wide (its rim's radius) and tall the vortex is, by how full it is. */
    private static float rim(float full) {
        return 0.12F + 0.3F * full;
    }

    private static float depth(float full) {
        return 0.1F + 0.56F * full;
    }

    public WhirlpoolRenderer(BlockEntityRendererProvider.Context context) {}

    /** One fluid as it's drawn: its picture, tint, brightness, how much of the whole. */
    private record Look(TextureAtlasSprite sprite, int argb, boolean glows, float share, boolean gas, int density) {}

    private static Look look(FluidStack fluid, long amount, long total) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fluid));
        int tint = ext.getTintColor(fluid);
        net.neoforged.neoforge.fluids.FluidType type = fluid.getFluid().getFluidType();
        return new Look(sprite, tint | 0xFF000000, type.getLightLevel(fluid) > 0, total <= 0 ? 0 : (float) amount / total,
                type.isLighterThanAir(), type.getDensity(fluid));
    }

    @Override
    public void render(WhirlpoolBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (be.entries().isEmpty()) {
            return;
        }
        List<Look> looks = new ArrayList<>();
        for (WhirlpoolBlockEntity.Entry e : be.entries()) {
            looks.add(look(e.fluid, e.amount, be.total()));
        }
        float full = be.fullness();
        be.shownFull = be.shownFull < 0 ? full : be.shownFull + (full - be.shownFull) * 0.05F;
        float seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0F;
        if (be.tank() > 0) {
            tank(be, looks, poseStack, buffers, light, seconds);
        } else if (be.still()) {
            // (a plain tank: what it holds in layers inside its frame)
            layers(be, looks, poseStack, buffers.getBuffer(Types.POOL), light, seconds,
                    0.135F, 0.19F, 0.865F, 0.86F, false);
        } else {
            // (alone: it starts small at the bottom of its frame, and grows wider and taller the more it holds)
            float grown = Math.max(be.shownFull, 0.05F);
            vortex(looks, rim(grown), depth(grown), BOTTOM + depth(grown), poseStack, buffers, light, seconds);
        }
    }

    // ---- alone: the vortex ----

    // ---- the whirlpool shader: the funnel's fluids flowing round and down it, their bands stirred into each other ----

    static ShaderInstance shader;
    /** (This whirlpool's bands, for the shader: their pictures' rectangles, tints, where each ends; how many.) */
    private static final float[] RECTS = new float[24], TINTS = new float[24], EDGES = new float[8];

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "whirlpool"), DefaultVertexFormat.PARTICLE, s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType FUNNEL = create("skyblocks_whirlpool", DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 8192,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTextureState(new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
                        .setTexturingState(new TexturingStateShard("skyblocks_whirlpool_bands", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("Time").set((System.currentTimeMillis() % 3_600_000L) / 1000.0F);
                                for (int i = 0; i < 6; i++) {
                                    shader.safeGetUniform("Rect" + i).set(RECTS[i * 4], RECTS[i * 4 + 1], RECTS[i * 4 + 2], RECTS[i * 4 + 3]);
                                    shader.safeGetUniform("Tint" + i).set(TINTS[i * 4], TINTS[i * 4 + 1], TINTS[i * 4 + 2], TINTS[i * 4 + 3]);
                                }
                                shader.safeGetUniform("EdgesA").set(EDGES[0], EDGES[1], EDGES[2], EDGES[3]);
                                shader.safeGetUniform("EdgesB").set(EDGES[4], EDGES[5], EDGES[6], EDGES[7]);
                            }
                        }, () -> {}))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(LIGHTMAP)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(true));

        /**
         * The fluids' textured pieces (the pool, the plain tank's layers): like an entity's see-through skin, but drawn into
         * the main picture - with Fabulous graphics an entity's goes to a picture of its own, mixed in afterwards by
         * depth, and the glass round the tank came out wrong (parts of the pool missing behind it).
         */
        static final RenderType POOL = create("skyblocks_whirlpool_pool", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536,
                true, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(true));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /** The vortex, its rim this wide round the block's middle at {@code top}, its throat {@code depth} below. */
    private static void vortex(List<Look> looks, float rim, float depth, float top, PoseStack poseStack, MultiBufferSource buffers, int light, float t) {
        if (shader != null && buffers instanceof MultiBufferSource.BufferSource source) {
            funnel(looks, rim, depth, top, poseStack, source, light);
            return;
        }
        vortexWith(looks, rim, depth, top, poseStack, buffers.getBuffer(Types.POOL), light, t);
    }

    /** (Without the shader: the funnel's rings, each band its fluid's picture.) */
    private static void vortexWith(List<Look> looks, float rim, float depth, float top, PoseStack poseStack, VertexConsumer v, int light, float t) {
        // bands from the rim inwards: the most first
        List<Look> bands = new ArrayList<>(looks);
        bands.sort((a, b) -> Float.compare(b.share, a.share));
        if (bands.size() > 6) {
            bands = bands.subList(0, 6);
        }
        float sum = 0;
        for (Look l : bands) {
            sum += l.share;
        }
        float throat = 0.035F;
        PoseStack.Pose pose = poseStack.last();
        float[] r = new float[RINGS + 1], y = new float[RINGS + 1], turn = new float[RINGS + 1];
        for (int k = 0; k <= RINGS; k++) {
            float u = (float) k / RINGS;
            r[k] = throat + (rim - throat) * (float) Math.pow(1.0 - u, 1.4);
            y[k] = top - depth * (float) Math.pow(u, 1.8);
            // (a spiral, turning as one: each ring turned a little further than the one outside it - by a fixed amount, so
            // the funnel's pieces never twist round on themselves as time goes on)
            turn[k] = (t * 1.3F) % (float) (Math.PI * 2) + 2.2F * (float) Math.log(rim / r[k]);
        }
        float acc = 0;
        int band = 0;
        for (int k = 0; k < RINGS; k++) {
            // which band this ring is in
            float mid = (k + 0.5F) / RINGS * sum;
            while (band < bands.size() - 1 && mid > acc + bands.get(band).share) {
                acc += bands.get(band).share;
                band++;
            }
            Look l = bands.get(band);
            int lit = l.glows ? LightTexture.FULL_BRIGHT : light;
            int argb = (l.argb & 0x00FFFFFF) | 0xE6000000;
            for (int s = 0; s < SEGMENTS; s++) {
                double a0 = Math.PI * 2 * s / SEGMENTS, a1 = Math.PI * 2 * (s + 1) / SEGMENTS;
                float x00 = 0.5F + r[k] * (float) Math.cos(a0 + turn[k]), z00 = 0.5F + r[k] * (float) Math.sin(a0 + turn[k]);
                float x01 = 0.5F + r[k] * (float) Math.cos(a1 + turn[k]), z01 = 0.5F + r[k] * (float) Math.sin(a1 + turn[k]);
                float x10 = 0.5F + r[k + 1] * (float) Math.cos(a0 + turn[k + 1]), z10 = 0.5F + r[k + 1] * (float) Math.sin(a0 + turn[k + 1]);
                float x11 = 0.5F + r[k + 1] * (float) Math.cos(a1 + turn[k + 1]), z11 = 0.5F + r[k + 1] * (float) Math.sin(a1 + turn[k + 1]);
                float u0 = (s % 4) / 4.0F, u1 = u0 + 0.25F, v0 = (k % 4) / 4.0F, v1 = v0 + 0.25F;
                // (darker deeper in; lighter spiral arms of foam winding down it)
                double arm = Math.sin(3.0 * (a0 + turn[k]) + Math.log(r[k] / rim) * 6.0);
                int shade = shade(argb, (1.0F - 0.55F * k / RINGS) * (0.8F + 0.35F * (float) Math.max(0.0, arm)));
                vertex(v, pose, x00, y[k], z00, l.sprite, u0, v0, shade, lit);
                vertex(v, pose, x10, y[k + 1], z10, l.sprite, u0, v1, shade, lit);
                vertex(v, pose, x11, y[k + 1], z11, l.sprite, u1, v1, shade, lit);
                vertex(v, pose, x01, y[k], z01, l.sprite, u1, v0, shade, lit);
            }
        }
        // the throat: dark
        Look l = bands.get(bands.size() - 1);
        for (int s = 0; s < SEGMENTS; s++) {
            double a0 = Math.PI * 2 * s / SEGMENTS, a1 = Math.PI * 2 * (s + 1) / SEGMENTS;
            float yb = y[RINGS];
            vertex(v, pose, 0.5F, yb - 0.03F, 0.5F, l.sprite, 0.5F, 0.5F, 0xF0050A10, light);
            vertex(v, pose, 0.5F, yb - 0.03F, 0.5F, l.sprite, 0.5F, 0.5F, 0xF0050A10, light);
            vertex(v, pose, 0.5F + throat * (float) Math.cos(a1), yb, 0.5F + throat * (float) Math.sin(a1), l.sprite, 0.6F, 0.6F, 0xF0050A10, light);
            vertex(v, pose, 0.5F + throat * (float) Math.cos(a0), yb, 0.5F + throat * (float) Math.sin(a0), l.sprite, 0.4F, 0.6F, 0xF0050A10, light);
        }
    }

    /** The funnel, drawn now with the whirlpool shader: still rings, the fluids flowing over them in the shader. */
    private static void funnel(List<Look> looks, float rim, float depth, float top, PoseStack poseStack, MultiBufferSource.BufferSource source, int light) {
        bands(looks);
        // (nothing else flushed: only its own batch, drawn at once below with this whirlpool's uniforms)
        VertexConsumer v = source.getBuffer(Types.FUNNEL);
        funnelMesh(v, poseStack.last(), 0.5F, 0.5F, rim, 0.035F, depth, top, light);
        source.endBatch(Types.FUNNEL);
    }

    /** This whirlpool's fluids, for the shader: the most first (outermost), at most six. */
    private static List<Look> bands(List<Look> looks) {
        List<Look> bands = new ArrayList<>(looks);
        bands.sort((a, b) -> Float.compare(b.share, a.share));
        if (bands.size() > 6) {
            bands = bands.subList(0, 6);
        }
        float sum = 0;
        for (Look l : bands) {
            sum += l.share;
        }
        float acc = 0;
        java.util.Arrays.fill(EDGES, 1.0F);
        for (int i = 0; i < 6; i++) {
            Look l = bands.get(Math.min(i, bands.size() - 1));
            RECTS[i * 4] = l.sprite.getU0();
            RECTS[i * 4 + 1] = l.sprite.getV0();
            RECTS[i * 4 + 2] = l.sprite.getU1();
            RECTS[i * 4 + 3] = l.sprite.getV1();
            TINTS[i * 4] = ((l.argb >> 16) & 0xFF) / 255.0F;
            TINTS[i * 4 + 1] = ((l.argb >> 8) & 0xFF) / 255.0F;
            TINTS[i * 4 + 2] = (l.argb & 0xFF) / 255.0F;
            TINTS[i * 4 + 3] = l.glows ? 1.0F : 0.0F;
            if (i < bands.size()) {
                acc += l.share;
                EDGES[i] = sum > 0 ? acc / sum : 1.0F;
            }
        }
        EDGES[6] = bands.size();
        return bands;
    }

    /** The funnel's rings round (cx, cz): its rim at {@code top}, down {@code depth} to its throat. */
    private static void funnelMesh(VertexConsumer v, PoseStack.Pose pose, float cx, float cz, float rim, float throat, float depth, float top, int light) {
        float[] r = new float[RINGS + 1], y = new float[RINGS + 1];
        for (int k = 0; k <= RINGS; k++) {
            float u = (float) k / RINGS;
            r[k] = throat + (rim - throat) * (float) Math.pow(1.0 - u, 1.4);
            y[k] = top - depth * (float) Math.pow(u, 1.8);
        }
        int segments = SEG;
        for (int k = 0; k < RINGS; k++) {
            int c0 = funnelColour(k), c1 = funnelColour(k + 1);
            float s0 = (float) k / RINGS, s1 = (float) (k + 1) / RINGS;
            for (int s = 0; s < segments; s++) {
                float u0 = (float) s / segments, u1 = (float) (s + 1) / segments;
                float ca = COS[s], sa = SIN[s], cb = COS[s + 1], sb = SIN[s + 1];
                funnelVertex(v, pose, cx + r[k] * ca, y[k], cz + r[k] * sa, u0, s0, c0, light);
                funnelVertex(v, pose, cx + r[k + 1] * ca, y[k + 1], cz + r[k + 1] * sa, u0, s1, c1, light);
                funnelVertex(v, pose, cx + r[k + 1] * cb, y[k + 1], cz + r[k + 1] * sb, u1, s1, c1, light);
                funnelVertex(v, pose, cx + r[k] * cb, y[k], cz + r[k] * sb, u1, s0, c0, light);
            }
        }
    }

    /** (The funnel's way round, worked out once: SEG pieces.) */
    private static final int SEG = 32;
    private static final float[] COS = new float[SEG + 1], SIN = new float[SEG + 1];

    static {
        for (int i = 0; i <= SEG; i++) {
            double a = Math.PI * 2 * i / SEG;
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
        COS[SEG] = COS[0];
        SIN[SEG] = SIN[0];
    }

    /** (Darker deeper in.) */
    private static int funnelColour(int ring) {
        int g = (int) (255 * (1.0F - 0.5F * ring / RINGS));
        return 0xE6000000 | (g << 16) | (g << 8) | g;
    }

    private static void funnelVertex(VertexConsumer v, PoseStack.Pose pose, float x, float y, float z, float u, float w, int argb, int light) {
        v.addVertex(pose, x, y, z).setUv(u, w).setColor(argb).setLight(light);
    }

    // ---- in its tank: the tank filled, in layers ----

    private static void tank(WhirlpoolBlockEntity be, List<Look> looks, PoseStack poseStack, MultiBufferSource buffers, int light, float t) {
        tankInside(be, looks, poseStack, buffers, light, t);

    }

    private static void tankInside(WhirlpoolBlockEntity be, List<Look> looks, PoseStack poseStack, MultiBufferSource buffers, int light, float t) {
        int r = (be.tank() - 1) / 2;
        float inset = 0.01F;
        if (be.still()) {
            layers(be, looks, poseStack, buffers.getBuffer(Types.POOL), light, t,
                    -(r - 1) + inset, -(r - 1) + inset, r - inset, r - inset, false);
            return;
        }
        float lo = -(r - 1) + inset, hi = r - inset;
        if (shader != null && buffers instanceof MultiBufferSource.BufferSource source) {
            tankWhirlpool(be, looks, poseStack, source, light, lo, hi);
        } else { // (without the shader: the funnel alone, filling the tank)
            float width = 2 * r - 1, full = Math.max(be.shownFull, 0.05F);
            poseStack.pushPose();
            poseStack.translate(0.5F, -(r - 1), 0.5F);
            poseStack.scale(width, width, width);
            poseStack.translate(-0.5F, 0.0F, -0.5F);
            float depth = 0.1F + 0.82F * full;
            vortex(looks, rim(full), depth, 0.03F + depth, poseStack, buffers, light, t);
            poseStack.popPose();
        }
    }

    /**
     * The whirlpool look in its tank: a pool of what it holds reaching every wall, its surface swirling in a spiral; the
     * funnel dipping from its middle (half the tank across even nearly empty, wider the fuller); below the funnel's
     * throat a twisting spout down to the floor, landing in a ring of foam.
     */
    private static void tankWhirlpool(WhirlpoolBlockEntity be, List<Look> looks, PoseStack poseStack, MultiBufferSource.BufferSource source,
                                      int light, float lo, float hi) {
        float full = Math.max(be.shownFull, 0.02F);
        float width = hi - lo, half = width / 2.0F, height = hi - lo;
        float surface = lo + height * (0.3F + 0.6F * full);   // (the pool rises as it fills)
        float rim = width * (0.25F + 0.17F * full);            // (half the tank across at the least)
        float throatY = lo + height * 0.14F, throat = Math.max(0.07F, width * 0.035F);
        PoseStack.Pose pose = poseStack.last();
        List<Look> bands = bands(looks);
        VertexConsumer v = source.getBuffer(Types.FUNNEL);
        // its surface: from the walls (s -1) in to the funnel's lip (s 0), round it
        int segments = 48, rings = 8;
        for (int j = 0; j < segments; j++) {
            double a0 = Math.PI * 2 * j / segments, a1 = Math.PI * 2 * (j + 1) / segments;
            float ca0 = (float) Math.cos(a0), sa0 = (float) Math.sin(a0), ca1 = (float) Math.cos(a1), sa1 = (float) Math.sin(a1);
            float w0 = wall(a0, half), w1 = wall(a1, half);
            float u0 = (float) j / segments, u1 = (float) (j + 1) / segments;
            for (int m = 0; m < rings; m++) {
                float f0 = (float) m / rings, f1 = (float) (m + 1) / rings;
                float ra0 = w0 + (rim - w0) * f0, rb0 = w0 + (rim - w0) * f1, ra1 = w1 + (rim - w1) * f0, rb1 = w1 + (rim - w1) * f1;
                int c = 0xD8FFFFFF;
                funnelVertex(v, pose, 0.5F + ra0 * ca0, surface, 0.5F + ra0 * sa0, u0, f0 - 1.0F, c, light);
                funnelVertex(v, pose, 0.5F + rb0 * ca0, surface, 0.5F + rb0 * sa0, u0, f1 - 1.0F, c, light);
                funnelVertex(v, pose, 0.5F + rb1 * ca1, surface, 0.5F + rb1 * sa1, u1, f1 - 1.0F, c, light);
                funnelVertex(v, pose, 0.5F + ra1 * ca1, surface, 0.5F + ra1 * sa1, u1, f0 - 1.0F, c, light);
            }
        }
        // the funnel, from the lip down to its throat
        funnelMesh(v, pose, 0.5F, 0.5F, rim, throat, surface - throatY, surface, light);
        // the spout: a twisting column down to the floor (the innermost fluid)
        int pieces = 6;
        for (int k = 0; k < pieces; k++) {
            float y0 = throatY - (throatY - lo) * k / pieces, y1 = throatY - (throatY - lo) * (k + 1) / pieces;
            float r0 = throat * (1.0F - 0.25F * k / pieces), r1 = throat * (1.0F - 0.25F * (k + 1) / pieces);
            float s0 = 0.74F + 0.06F * k / pieces, s1 = 0.74F + 0.06F * (k + 1) / pieces;
            for (int j = 0; j < 16; j++) {
                double a0 = Math.PI * 2 * j / 16, a1 = Math.PI * 2 * (j + 1) / 16;
                float ca0 = (float) Math.cos(a0), sa0 = (float) Math.sin(a0), ca1 = (float) Math.cos(a1), sa1 = (float) Math.sin(a1);
                float u0 = j / 16.0F, u1 = (j + 1) / 16.0F;
                funnelVertex(v, pose, 0.5F + r0 * ca0, y0, 0.5F + r0 * sa0, u0, s0, 0xF0FFFFFF, light);
                funnelVertex(v, pose, 0.5F + r1 * ca0, y1, 0.5F + r1 * sa0, u0, s1, 0xF0FFFFFF, light);
                funnelVertex(v, pose, 0.5F + r1 * ca1, y1, 0.5F + r1 * sa1, u1, s1, 0xF0FFFFFF, light);
                funnelVertex(v, pose, 0.5F + r0 * ca1, y0, 0.5F + r0 * sa1, u1, s0, 0xF0FFFFFF, light);
            }
        }
        // where it lands: a ring of foam on the floor (s round 0: the shader's foam)
        float ry = lo + 0.02F, in = throat * 0.8F, out = throat * 3.5F;
        for (int j = 0; j < 24; j++) {
            double a0 = Math.PI * 2 * j / 24, a1 = Math.PI * 2 * (j + 1) / 24;
            float ca0 = (float) Math.cos(a0), sa0 = (float) Math.sin(a0), ca1 = (float) Math.cos(a1), sa1 = (float) Math.sin(a1);
            float u0 = j / 24.0F, u1 = (j + 1) / 24.0F;
            funnelVertex(v, pose, 0.5F + out * ca0, ry, 0.5F + out * sa0, u0, -0.06F, 0xE0FFFFFF, light);
            funnelVertex(v, pose, 0.5F + in * ca0, ry, 0.5F + in * sa0, u0, 0.06F, 0xE0FFFFFF, light);
            funnelVertex(v, pose, 0.5F + in * ca1, ry, 0.5F + in * sa1, u1, 0.06F, 0xE0FFFFFF, light);
            funnelVertex(v, pose, 0.5F + out * ca1, ry, 0.5F + out * sa1, u1, -0.06F, 0xE0FFFFFF, light);
        }
        source.endBatch(Types.FUNNEL);
        // the pool's sides against the glass and its floor (the outermost fluid), see-through - drawn after the funnel, so
        // from the side the funnel is seen through the water, not hidden behind it
        box(source.getBuffer(Types.POOL), pose, bands.get(0), lo, lo, lo, hi, surface, hi,
                true, -1.0F, 0.0F, light, 0.0F, 0.42F);
    }

    /** How far from the tank's middle its wall is, this way round (the tank square, {@code half} to each side). */
    private static float wall(double a, float half) {
        return half / (float) Math.max(Math.abs(Math.cos(a)), Math.abs(Math.sin(a)));
    }

    /**
     * What it holds in layers in the box from (lo, lo, lo) to (hi, top, hi): heavy at the bottom, gases at the top; the
     * whirlpool swirling down from the liquid's surface in the middle, if {@code swirling}.
     */
    private static void layers(WhirlpoolBlockEntity be, List<Look> looks, PoseStack poseStack, VertexConsumer v, int light, float t,
                               float lo, float bottomY, float hi, float topY, boolean swirling) {
        float x0 = lo, x1 = hi, z0 = lo, z1 = hi, floor = bottomY, ceiling = topY;
        float height = ceiling - floor;
        float filled = height * Math.min(1.0F, be.shownFull);
        // heavy at the bottom, gases hanging from the top
        List<Look> liquids = new ArrayList<>(), gases = new ArrayList<>();
        for (Look l : looks) {
            (l.gas ? gases : liquids).add(l);
        }
        liquids.sort((a, b) -> Integer.compare(b.density, a.density));
        gases.sort((a, b) -> Integer.compare(a.density, b.density));
        float liquidShare = 0;
        for (Look l : liquids) {
            liquidShare += l.share;
        }
        // (how much the surface moves: a while after something came in or went out, then barely)
        float since = (System.currentTimeMillis() - be.rippleAt) / 1000.0F;
        float ripple = (0.015F + 0.07F * Math.max(0.0F, 1.0F - since / 2.5F)) * Math.min(1.0F, (hi - lo) / 3.0F);
        PoseStack.Pose pose = poseStack.last();
        // the whirlpool itself, swirling down from the liquid's surface in the middle of the tank (as wide as most of
        // the tank, as deep as the liquid lets it be)
        float liquidH = filled * liquidShare;
        float width = hi - lo;
        float scale = width * 0.38F / TANK_RIM, scaleY = Math.min(scale, liquidH * 0.85F / TANK_DEPTH);
        boolean swirl = swirling && !liquids.isEmpty() && liquidH > 0.3F;
        float hole = swirl ? TANK_RIM * scale * 0.9F : 0.0F;
        float y = floor;
        for (int i = 0; i < liquids.size(); i++) {
            Look l = liquids.get(i);
            float h = filled * l.share;
            boolean topmost = i == liquids.size() - 1;
            box(v, pose, l, x0, y, z0, x1, y + h, z1, i == 0, topmost ? ripple : -1.0F, hole, light, t, 0.85F);
            y += h;
        }
        if (swirl) {
            poseStack.pushPose();
            poseStack.translate(0.5F, floor + liquidH + 0.01F, 0.5F);
            poseStack.scale(scale, scaleY, scale);
            poseStack.translate(-0.5F, -TANK_DEPTH, -0.5F);
            vortexWith(liquids, TANK_RIM, TANK_DEPTH, TANK_DEPTH, poseStack, v, light, t);
            poseStack.popPose();
        }
        float yTop = ceiling;
        for (int i = 0; i < gases.size(); i++) {
            Look l = gases.get(i);
            float h = filled * l.share;
            box(v, pose, l, x0, yTop - h, z0, x1, yTop, z1, true, -1.0F, 0.0F, light, t, 0.45F);
            yTop -= h;
        }
    }

    /**
     * A box of one fluid, its sides cut into block-sized squares (its picture once a block). {@code ripple} >= 0: its
     * top drawn, rippling that much, with a round hole {@code hole} wide round the block's middle (the whirlpool's);
     * {@code bottom}: its bottom drawn.
     */
    private static void box(VertexConsumer v, PoseStack.Pose pose, Look l, float x0, float y0, float z0, float x1, float y1, float z1,
                            boolean bottom, float ripple, float hole, int light, float t, float alpha) {
        if (y1 - y0 < 0.002F) {
            return;
        }
        int lit = l.glows ? LightTexture.FULL_BRIGHT : light;
        int argb = (l.argb & 0x00FFFFFF) | ((int) (alpha * 255) << 24);
        int side = shade(argb, 0.8F);
        // the four sides
        for (float a = x0; a < x1 - 0.001F; a = Math.min(x1, (float) Math.floor(a) + 1)) {
            float b = Math.min(x1, (float) Math.floor(a) + 1);
            column(v, pose, l, a, z0, b, z0, y0, y1, side, lit);
            column(v, pose, l, b, z1, a, z1, y0, y1, side, lit);
        }
        for (float a = z0; a < z1 - 0.001F; a = Math.min(z1, (float) Math.floor(a) + 1)) {
            float b = Math.min(z1, (float) Math.floor(a) + 1);
            column(v, pose, l, x0, b, x0, a, y0, y1, side, lit);
            column(v, pose, l, x1, a, x1, b, y0, y1, side, lit);
        }
        // top (rippling, a quarter block a square, round the whirlpool's hole)
        if (ripple >= 0) {
            for (float a = x0; a < x1 - 0.001F; a = Math.min(x1, (float) Math.floor(a * 4) / 4 + 0.25F)) {
                float b = Math.min(x1, (float) Math.floor(a * 4) / 4 + 0.25F);
                for (float c = z0; c < z1 - 0.001F; c = Math.min(z1, (float) Math.floor(c * 4) / 4 + 0.25F)) {
                    float d = Math.min(z1, (float) Math.floor(c * 4) / 4 + 0.25F);
                    float mx = (a + b) * 0.5F - 0.5F, mz = (c + d) * 0.5F - 0.5F;
                    if (mx * mx + mz * mz < hole * hole) {
                        continue;
                    }
                    float u0 = frac(a), u1 = u0 + (b - a), v0 = frac(c), v1 = v0 + (d - c);
                    float yaa = y1 + wave(a, c, t, ripple), yab = y1 + wave(a, d, t, ripple), ybb = y1 + wave(b, d, t, ripple), yba = y1 + wave(b, c, t, ripple);
                    vertex(v, pose, a, yaa, c, l.sprite, u0, v0, argb, lit);
                    vertex(v, pose, a, yab, d, l.sprite, u0, v1, argb, lit);
                    vertex(v, pose, b, ybb, d, l.sprite, u1, v1, argb, lit);
                    vertex(v, pose, b, yba, c, l.sprite, u1, v0, argb, lit);
                }
            }
        }
        // bottom, a square a block
        for (float a = x0; a < x1 - 0.001F; a = Math.min(x1, (float) Math.floor(a) + 1)) {
            float b = Math.min(x1, (float) Math.floor(a) + 1);
            for (float c = z0; c < z1 - 0.001F; c = Math.min(z1, (float) Math.floor(c) + 1)) {
                float d = Math.min(z1, (float) Math.floor(c) + 1);
                float u0 = frac(a), u1 = u0 + (b - a), v0 = frac(c), v1 = v0 + (d - c);
                if (bottom) {
                    int under = shade(argb, 0.6F);
                    vertex(v, pose, a, y0, c, l.sprite, u0, v0, under, lit);
                    vertex(v, pose, b, y0, c, l.sprite, u1, v0, under, lit);
                    vertex(v, pose, b, y0, d, l.sprite, u1, v1, under, lit);
                    vertex(v, pose, a, y0, d, l.sprite, u0, v1, under, lit);
                }
            }
        }
    }

    /** One side's column (from (xa, za) to (xb, zb)), cut into blocks up its height. */
    private static void column(VertexConsumer v, PoseStack.Pose pose, Look l, float xa, float za, float xb, float zb, float y0, float y1, int argb, int lit) {
        float w = Math.abs(xb - xa) + Math.abs(zb - za);
        for (float a = y0; a < y1 - 0.001F; a = Math.min(y1, (float) Math.floor(a) + 1)) {
            float b = Math.min(y1, (float) Math.floor(a) + 1);
            float v0 = 1.0F - (b - (float) Math.floor(a)), v1 = 1.0F - (a - (float) Math.floor(a));
            vertex(v, pose, xa, b, za, l.sprite, 0.0F, v0, argb, lit);
            vertex(v, pose, xa, a, za, l.sprite, 0.0F, v1, argb, lit);
            vertex(v, pose, xb, a, zb, l.sprite, w, v1, argb, lit);
            vertex(v, pose, xb, b, zb, l.sprite, w, v0, argb, lit);
        }
    }

    private static float wave(float x, float z, float t, float amount) {
        return amount * (float) (Math.sin(x * 2.3 + t * 2.1) * 0.6 + Math.sin(z * 1.9 - t * 1.7) * 0.4) - amount;
    }

    private static float frac(float a) {
        return a - (float) Math.floor(a);
    }

    private static int shade(int argb, float k) {
        int r = (int) (((argb >> 16) & 0xFF) * k), g = (int) (((argb >> 8) & 0xFF) * k), b = (int) ((argb & 0xFF) * k);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static void vertex(VertexConsumer v, PoseStack.Pose pose, float x, float y, float z, TextureAtlasSprite sprite, float u, float w,
                               int argb, int light) {
        v.addVertex(pose, x, y, z).setColor(argb).setUv(sprite.getU(Math.min(1.0F, u)), sprite.getV(Math.min(1.0F, w)))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public AABB getRenderBoundingBox(WhirlpoolBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(be.tank() > 0 ? be.tank() / 2.0 + 0.5 : 0.1);
    }

    @Override
    public boolean shouldRenderOffScreen(WhirlpoolBlockEntity be) {
        return be.tank() > 0;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
