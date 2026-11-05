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
    /**
     * (Its shape, for the shader: alone, 6 times its fluids' pictures round it, its throat dark; in a tank (z 1) see
     * tankWhirlpool and the shader's tank().)
     */
    private static final float[] SHAPE = {6.0F, 1.0F, 0.0F, 0.0F};

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
                                shader.safeGetUniform("Shape").set(SHAPE[0], SHAPE[1], SHAPE[2], SHAPE[3]);
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
                        // (its depth not kept: whatever order it's drawn in - some mods put everything off to be drawn
                        // together later - the whirlpool behind the water is never hidden by it)
                        .setWriteMaskState(COLOR_WRITE)
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
        SHAPE[0] = 6.0F;
        SHAPE[1] = 1.0F;
        SHAPE[2] = 0.0F;
        SHAPE[3] = 0.0F;
        VertexConsumer v = source.getBuffer(Types.FUNNEL);
        funnelMesh(v, poseStack.last(), 0.5F, 0.5F, rim, 0.035F, depth, top, light, RINGS, 2, 0.5F);
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
    private static void funnelMesh(VertexConsumer v, PoseStack.Pose pose, float cx, float cz, float rim, float throat, float depth, float top, int light,
                                   int rings, int step, float darken) {
        float[] r = new float[rings + 1], y = new float[rings + 1];
        for (int k = 0; k <= rings; k++) {
            float u = (float) k / rings;
            r[k] = throat + (rim - throat) * (float) Math.pow(1.0 - u, 1.4);
            y[k] = top - depth * (float) Math.pow(u, 1.8);
        }
        for (int k = 0; k < rings; k++) {
            float s0 = (float) k / rings, s1 = (float) (k + 1) / rings;
            int c0 = funnelColour(s0, darken), c1 = funnelColour(s1, darken);
            for (int s = 0; s < SEG; s += step) {
                float u0 = (float) s / SEG, u1 = (float) (s + step) / SEG;
                float ca = COS[s], sa = SIN[s], cb = COS[s + step], sb = SIN[s + step];
                funnelVertex(v, pose, cx + r[k] * ca, y[k], cz + r[k] * sa, u0, s0, c0, light);
                funnelVertex(v, pose, cx + r[k + 1] * ca, y[k + 1], cz + r[k + 1] * sa, u0, s1, c1, light);
                funnelVertex(v, pose, cx + r[k + 1] * cb, y[k + 1], cz + r[k + 1] * sb, u1, s1, c1, light);
                funnelVertex(v, pose, cx + r[k] * cb, y[k], cz + r[k] * sb, u1, s0, c0, light);
            }
        }
    }

    /** (The funnel's way round, worked out once: SEG pieces - alone, every other one.) */
    private static final int SEG = 64;
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
    private static int funnelColour(float down, float darken) {
        int g = (int) (255 * (1.0F - darken * down));
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
        // (for the shader: its pictures at most 32 times round, out at the walls; how far out its furthest corner is)
        float far = half * 1.45F;
        SHAPE[0] = 32.0F;
        SHAPE[1] = 0.0F;
        SHAPE[2] = 1.0F;
        SHAPE[3] = far;
        VertexConsumer v = source.getBuffer(Types.FUNNEL);
        // its surface: from the walls (s -1) in to the funnel's lip (s 0), round it - flat, so how far in (xi) is
        // log(lip / how far out)
        int rings = 8;
        for (int j = 0; j < SEG; j++) {
            float ca0 = COS[j], sa0 = SIN[j], ca1 = COS[j + 1], sa1 = SIN[j + 1];
            float w0 = wall(Math.PI * 2 * j / SEG, half), w1 = wall(Math.PI * 2 * (j + 1) / SEG, half);
            float u0 = (float) j / SEG, u1 = (float) (j + 1) / SEG;
            for (int m = 0; m < rings; m++) {
                float f0 = (float) m / rings, f1 = (float) (m + 1) / rings;
                float ra0 = w0 + (rim - w0) * f0, rb0 = w0 + (rim - w0) * f1, ra1 = w1 + (rim - w1) * f0, rb1 = w1 + (rim - w1) * f1;
                tankVertex(v, pose, ra0, ca0, sa0, surface, u0, (float) Math.log(rim / ra0), 1.0F, f0 - 1.0F, far, 0xD8, light);
                tankVertex(v, pose, rb0, ca0, sa0, surface, u0, (float) Math.log(rim / rb0), 1.0F, f1 - 1.0F, far, 0xD8, light);
                tankVertex(v, pose, rb1, ca1, sa1, surface, u1, (float) Math.log(rim / rb1), 1.0F, f1 - 1.0F, far, 0xD8, light);
                tankVertex(v, pose, ra1, ca1, sa1, surface, u1, (float) Math.log(rim / ra1), 1.0F, f0 - 1.0F, far, 0xD8, light);
            }
        }
        // the funnel from its lip down to its throat, then on down as the spout to the floor, flaring where it lands: one
        // surface turned round its middle, its profile worked out here - how far out, how high, which band (s), how
        // light, and how far in along it (xi: each bit of the way over how far out it is there)
        int funnelRings = 20, drop = Math.max(4, Math.round((throatY - lo) * 3.0F));
        int count = funnelRings + drop + 1;
        float[] pr = new float[count], py = new float[count], ps = new float[count], pl = new float[count], px = new float[count];
        float darken = 0.3F;
        for (int k = 0; k <= funnelRings; k++) {
            float u = (float) k / funnelRings;
            pr[k] = throat + (rim - throat) * (float) Math.pow(1.0 - u, 1.4);
            py[k] = surface - (surface - throatY) * (float) Math.pow(u, 1.8);
            ps[k] = u;
            pl[k] = 1.0F - darken * u;
        }
        for (int k = 1; k <= drop; k++) {
            float f = (float) k / drop;
            int i = funnelRings + k;
            py[i] = throatY - (throatY - lo) * f;
            pr[i] = throat * (1.0F - 0.3F * f + 0.9F * (float) Math.pow(f, 8));
            ps[i] = 1.0F;
            pl[i] = 1.0F - darken + darken * 0.5F * f; // (lighter towards the floor)
        }
        for (int k = 1; k < count; k++) {
            float dr = pr[k] - pr[k - 1], dy = py[k] - py[k - 1];
            px[k] = px[k - 1] + (float) Math.sqrt(dr * dr + dy * dy) / ((pr[k] + pr[k - 1]) * 0.5F);
        }
        for (int k = 0; k < count - 1; k++) {
            for (int j = 0; j < SEG; j++) {
                float ca0 = COS[j], sa0 = SIN[j], ca1 = COS[j + 1], sa1 = SIN[j + 1];
                float u0 = (float) j / SEG, u1 = (float) (j + 1) / SEG;
                tankVertex(v, pose, pr[k], ca0, sa0, py[k], u0, px[k], pl[k], ps[k], far, 0xE6, light);
                tankVertex(v, pose, pr[k + 1], ca0, sa0, py[k + 1], u0, px[k + 1], pl[k + 1], ps[k + 1], far, 0xE6, light);
                tankVertex(v, pose, pr[k + 1], ca1, sa1, py[k + 1], u1, px[k + 1], pl[k + 1], ps[k + 1], far, 0xE6, light);
                tankVertex(v, pose, pr[k], ca1, sa1, py[k], u1, px[k], pl[k], ps[k], far, 0xE6, light);
            }
        }
        // where it lands: a low crown of foam round its foot, thinning out across the floor (s 2 and up: the shader's
        // crown - the spout's fluid, foaming; fading to nothing at its edge)
        float foot = pr[count - 1], footXi = px[count - 1];
        float[] cr = {foot * 0.95F, foot * 1.6F, foot * 2.6F, foot * 3.8F};
        float[] cy = {lo + 0.16F, lo + 0.07F, lo + 0.025F, lo + 0.01F};
        float[] cs = {2.0F, 2.04F, 2.08F, 2.12F};
        int[] ca = {0xF0, 0xD0, 0x80, 0x00};
        for (int m = 0; m < 3; m++) {
            float x0 = footXi - (float) Math.log(cr[m] / foot), x1 = footXi - (float) Math.log(cr[m + 1] / foot);
            for (int j = 0; j < SEG; j += 2) {
                float c0 = COS[j], s0 = SIN[j], c1 = COS[j + 2], s1 = SIN[j + 2];
                float u0 = (float) j / SEG, u1 = (float) (j + 2) / SEG;
                tankVertex(v, pose, cr[m + 1], c0, s0, cy[m + 1], u0, x1, 1.0F, cs[m + 1], far, ca[m + 1], light);
                tankVertex(v, pose, cr[m], c0, s0, cy[m], u0, x0, 1.0F, cs[m], far, ca[m], light);
                tankVertex(v, pose, cr[m], c1, s1, cy[m], u1, x0, 1.0F, cs[m], far, ca[m], light);
                tankVertex(v, pose, cr[m + 1], c1, s1, cy[m + 1], u1, x1, 1.0F, cs[m + 1], far, ca[m + 1], light);
            }
        }
        source.endBatch(Types.FUNNEL);
        // the pool's sides against the glass and its floor (the outermost fluid), see-through - drawn after the funnel, so
        // from the side the funnel is seen through the water, not hidden behind it
        box(source.getBuffer(Types.POOL), pose, bands.get(0), lo, lo, lo, hi, surface, hi,
                true, -1.0F, 0.0F, light, 0.0F, 0.42F);
        source.endBatch(Types.POOL); // (right after the whirlpool, over it)
    }

    /**
     * One point of the whirlpool in its tank, {@code r} out from its middle this way round ({@code c}, {@code sn}), for
     * the shader: round it ({@code u}), how far in along it ({@code xi}); in its colour how light, which band ({@code s}),
     * how far out (of {@code far}), how see-through.
     */
    private static void tankVertex(VertexConsumer v, PoseStack.Pose pose, float r, float c, float sn, float y, float u, float xi,
                                   float shade, float s, float far, int alpha, int light) {
        int red = Math.round(255 * Math.min(1.0F, shade)), green = Math.round(255 * Math.max(0.0F, Math.min(1.0F, (s + 1.0F) / 3.2F))),
                blue = Math.round(255 * Math.min(1.0F, r / far));
        v.addVertex(pose, 0.5F + r * c, y, 0.5F + r * sn).setUv(u, xi).setColor(red, green, blue, alpha).setLight(light);
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
