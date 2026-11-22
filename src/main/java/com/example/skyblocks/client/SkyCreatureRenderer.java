package com.example.skyblocks.client;

import com.example.skyblocks.SkyCreature;
import com.example.skyblocks.SkyJellyfish;
import com.example.skyblocks.SkyMoth;
import com.example.skyblocks.SkyWhale;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Sky creatures (SkyCreature): blocky bodies whose every side shows the creature's sky (Shadered's render type for it,
 * like sky blocks and liquids), a few small parts (eyes, a moth's body) solid. Each kind poses its boxes each frame
 * ({@link #build}): a jellyfish's bell pulsing and tentacles swaying, a whale's tail and fins, a moth's wings beating.
 */
public abstract class SkyCreatureRenderer<T extends SkyCreature> extends EntityRenderer<T> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("skyblocks", "textures/entity/white.png");
    private static ByteBufferBuilder memory;

    /** This frame's sky boxes (x, y, z, u, v a vertex, posed) and solid boxes (x, y, z, colour, normal x, y, z). */
    private float[] sky = new float[4096];
    private int skySize;
    private float[] solid = new float[1024];
    private int solidSize;

    protected SkyCreatureRenderer(EntityRendererProvider.Context context, float shadow) {
        super(context);
        this.shadowRadius = shadow;
    }

    /** Poses this creature's boxes (box(...) for sky, solid(...) for solid parts), facing +z, feet at 0. */
    protected abstract void build(T entity, float time, PoseStack ps);

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        skySize = 0;
        solidSize = 0;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        build(entity, entity.tickCount + partialTick, ps);
        ps.popPose();
        if (solidSize > 0) {
            VertexConsumer c = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
            for (int i = 0; i < solidSize; i += 7) {
                int col = Float.floatToRawIntBits(solid[i + 3]);
                c.addVertex(solid[i], solid[i + 1], solid[i + 2]).setColor((col >> 16) & 255, (col >> 8) & 255, col & 255, 255)
                        .setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(solid[i + 4], solid[i + 5], solid[i + 6]);
            }
        }
        RenderType type = SkyLiquidRenderer.skyType(entity.sky());
        if (type != null && skySize > 0) {
            float[] d = java.util.Arrays.copyOf(sky, skySize);
            var data = com.noodlegamer76.shadered.client.renderer.SkyblockRenderer.getData(entity.sky());
            if (com.example.skyblocks.client.iris.IrisIslandTarget.deferSky()) {
                // with a shader pack: drawn at the end of the frame (see SkyLiquidRenderer)
                SkyUsage.ensureFresh(data);
                com.example.skyblocks.client.iris.IrisIslandTarget.deferSky(type, c -> emit(c, d));
            } else {
                // (only the part of its sky's picture it covers brought up to date, drawn now)
                Vec3 cam = entityRenderDispatcher.camera.getPosition();
                AABB box = entity.getBoundingBoxForCulling().inflate(0.5).move(-cam.x, -cam.y, -cam.z);
                SkyUsage.ensurePart(data, SkyUsage.rectOfBox((float) box.minX, (float) box.minY, (float) box.minZ,
                        (float) box.maxX, (float) box.maxY, (float) box.maxZ));
                if (memory == null) {
                    memory = new ByteBufferBuilder(1 << 16);
                }
                MultiBufferSource.BufferSource now = MultiBufferSource.immediate(memory);
                SkyUsage.placeKnown = true;
                try {
                    emit(now.getBuffer(type), d);
                    now.endBatch();
                } finally {
                    SkyUsage.placeKnown = false;
                }
            }
        }
        super.render(entity, yaw, partialTick, ps, buffers, light);
    }

    private static void emit(VertexConsumer c, float[] d) {
        for (int i = 0; i < d.length; i += 5) {
            c.addVertex(d[i], d[i + 1], d[i + 2]).setColor(255, 255, 255, 255).setUv(d[i + 3], d[i + 4]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return WHITE;
    }

    // ---- boxes ----

    private final Vector3f[] corners = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};

    private void corners(PoseStack ps, float x0, float y0, float z0, float x1, float y1, float z1) {
        Matrix4f m = ps.last().pose();
        for (int i = 0; i < 8; i++) {
            m.transformPosition((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1, corners[i]);
        }
    }

    /** The corners of each side: (bit 1 x, bit 2 y, bit 4 z) */
    private static final int[][] SIDES = {{0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}, {0, 4, 6, 2}, {1, 3, 7, 5}};
    private static final float[][] NORMALS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    /** A box showing the creature's sky (both sides of each face: the sky is the same, seen either way). */
    protected void box(PoseStack ps, float x0, float y0, float z0, float x1, float y1, float z1) {
        corners(ps, x0, y0, z0, x1, y1, z1);
        if (skySize + 6 * 8 * 5 > sky.length) {
            sky = java.util.Arrays.copyOf(sky, sky.length * 2);
        }
        for (int[] s : SIDES) {
            for (int k = 0; k < 4; k++) {
                skyVertex(corners[s[k]], k);
            }
            for (int k = 3; k >= 0; k--) {
                skyVertex(corners[s[k]], k);
            }
        }
    }

    private void skyVertex(Vector3f v, int k) {
        sky[skySize++] = v.x;
        sky[skySize++] = v.y;
        sky[skySize++] = v.z;
        sky[skySize++] = k == 1 || k == 2 ? 1 : 0;
        sky[skySize++] = k >= 2 ? 1 : 0;
    }

    /** A solid box of one colour (lit like a mob). */
    protected void solid(PoseStack ps, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        corners(ps, x0, y0, z0, x1, y1, z1);
        if (solidSize + 6 * 4 * 7 > solid.length) {
            solid = java.util.Arrays.copyOf(solid, solid.length * 2);
        }
        Vector3f n = new Vector3f();
        for (int f = 0; f < 6; f++) {
            ps.last().normal().transform(NORMALS[f][0], NORMALS[f][1], NORMALS[f][2], n);
            for (int k = 0; k < 4; k++) {
                Vector3f v = corners[SIDES[f][k]];
                solid[solidSize++] = v.x;
                solid[solidSize++] = v.y;
                solid[solidSize++] = v.z;
                solid[solidSize++] = Float.intBitsToFloat(rgb);
                solid[solidSize++] = n.x;
                solid[solidSize++] = n.y;
                solid[solidSize++] = n.z;
            }
        }
    }

    // ---- the creatures ----

    /** A bell that pulses (wider as it squeezes down), a scalloped rim, six tentacles swaying under it. */
    public static class Jellyfish extends SkyCreatureRenderer<SkyJellyfish> {
        public Jellyfish(EntityRendererProvider.Context context) {
            super(context, 0.0F);
        }

        @Override
        protected void build(SkyJellyfish e, float t, PoseStack ps) {
            float p = Mth.sin(t * 0.12F);
            float wide = 1.0F + 0.08F * Math.max(0.0F, p), tall = 1.0F - 0.07F * Math.max(0.0F, p);
            ps.pushPose();
            ps.translate(0.0F, 0.5F, 0.0F);
            ps.scale(wide, tall, wide);
            box(ps, -0.42F, 0.05F, -0.42F, 0.42F, 0.36F, 0.42F);
            box(ps, -0.33F, 0.36F, -0.33F, 0.33F, 0.5F, 0.33F);
            box(ps, -0.19F, 0.5F, -0.19F, 0.19F, 0.58F, 0.19F);
            for (int i = 0; i < 8; i++) {
                float a = i * Mth.TWO_PI / 8.0F, r = 0.36F;
                float cx = Mth.cos(a) * r, cz = Mth.sin(a) * r;
                box(ps, cx - 0.07F, -0.02F, cz - 0.07F, cx + 0.07F, 0.06F, cz + 0.07F);
            }
            ps.popPose();
            for (int i = 0; i < 6; i++) {
                float a = i * Mth.TWO_PI / 6.0F + 0.3F, r = 0.22F;
                float x = Mth.cos(a) * r, z = Mth.sin(a) * r;
                for (int k = 0; k < 4; k++) {
                    float sway = Mth.sin(t * 0.09F + i * 1.3F + k * 0.7F) * 0.045F * (k + 1);
                    float swayZ = Mth.cos(t * 0.07F + i * 0.9F + k * 0.6F) * 0.035F * (k + 1);
                    float top = 0.52F - k * 0.13F;
                    box(ps, x + sway - 0.035F, top - 0.13F, z + swayZ - 0.035F, x + sway + 0.035F, top, z + swayZ + 0.035F);
                }
            }
        }
    }

    /** A long body, a head, a tail and flukes that sweep up and down, fins that roll; small dark eyes. */
    public static class Whale extends SkyCreatureRenderer<SkyWhale> {
        public Whale(EntityRendererProvider.Context context) {
            super(context, 1.6F);
        }

        @Override
        protected void build(SkyWhale e, float t, PoseStack ps) {
            float bob = Mth.sin(t * 0.04F) * 0.08F;
            ps.pushPose();
            ps.translate(0.0F, bob, 0.0F);
            box(ps, -1.1F, 0.0F, -2.6F, 1.1F, 1.8F, 2.2F);
            box(ps, -1.0F, 0.1F, 2.2F, 1.0F, 1.6F, 3.2F);
            box(ps, -0.8F, 0.25F, 3.2F, 0.8F, 1.3F, 3.55F);
            solid(ps, 0.98F, 0.95F, 2.55F, 1.04F, 1.12F, 2.8F, 0x10101A);
            solid(ps, -1.04F, 0.95F, 2.55F, -0.98F, 1.12F, 2.8F, 0x10101A);
            // tail: a stock and flukes, swept up and down from the body's back
            float sweep = Mth.sin(t * 0.08F) * 14.0F;
            ps.pushPose();
            ps.translate(0.0F, 0.9F, -2.6F);
            ps.mulPose(Axis.XP.rotationDegrees(sweep));
            box(ps, -0.6F, -0.45F, -1.3F, 0.6F, 0.4F, 0.0F);
            ps.translate(0.0F, 0.0F, -1.3F);
            ps.mulPose(Axis.XP.rotationDegrees(sweep * 0.8F));
            box(ps, -0.35F, -0.2F, -0.5F, 0.35F, 0.15F, 0.0F);
            box(ps, -2.0F, -0.12F, -1.1F, 2.0F, 0.1F, -0.4F);
            ps.popPose();
            // fins, rolling gently
            float roll = Mth.sin(t * 0.08F + 1.0F) * 12.0F;
            for (int side = -1; side <= 1; side += 2) {
                ps.pushPose();
                ps.translate(side * 1.1F, 0.4F, 1.1F);
                ps.mulPose(Axis.ZP.rotationDegrees(side * roll));
                if (side > 0) {
                    box(ps, 0.0F, -0.1F, -0.5F, 1.3F, 0.08F, 0.5F);
                } else {
                    box(ps, -1.3F, -0.1F, -0.5F, 0.0F, 0.08F, 0.5F);
                }
                ps.popPose();
            }
            ps.popPose();
        }
    }

    /** A small dark body with feelers; two pairs of sky wings beating fast. */
    public static class Moth extends SkyCreatureRenderer<SkyMoth> {
        public Moth(EntityRendererProvider.Context context) {
            super(context, 0.0F);
        }

        @Override
        protected void build(SkyMoth e, float t, PoseStack ps) {
            float flap = Mth.sin(t * 1.3F + e.getId()) * 55.0F + 10.0F;
            solid(ps, -0.035F, 0.1F, -0.12F, 0.035F, 0.17F, 0.11F, 0x3B3550);
            solid(ps, -0.03F, 0.16F, 0.11F, -0.015F, 0.24F, 0.13F, 0x5A5278);
            solid(ps, 0.015F, 0.16F, 0.11F, 0.03F, 0.24F, 0.13F, 0x5A5278);
            for (int side = -1; side <= 1; side += 2) {
                ps.pushPose();
                ps.translate(side * 0.035F, 0.15F, 0.0F);
                ps.mulPose(Axis.ZP.rotationDegrees(side * flap));
                float x0 = side > 0 ? 0.0F : -0.3F, x1 = side > 0 ? 0.3F : 0.0F;
                box(ps, x0, -0.006F, -0.02F, x1, 0.006F, 0.15F);
                float y0 = side > 0 ? 0.0F : -0.2F, y1 = side > 0 ? 0.2F : 0.0F;
                box(ps, y0, -0.006F, -0.16F, y1, 0.006F, -0.02F);
                ps.popPose();
            }
        }
    }
}
