package com.example.skyblocks.client;

import com.example.skyblocks.SkyHolderBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Sky Rift: a jagged crack hanging in the air, always turned to face you, with its sky showing through it. Its
 * edges shimmer and crawl, glowing; little shards of the crack's edge float round it.
 */
public class SkyRiftRenderer implements BlockEntityRenderer<SkyHolderBlockEntity> {
    private static final int STEPS = 24;
    private static final float HEIGHT = 1.7F, WIDTH = 0.34F;
    private final float[] quads = new float[(STEPS + 8) * 40];

    public SkyRiftRenderer(BlockEntityRendererProvider.Context context) {}

    /** The crack's edge at height k (0 .. 1): its middle and half its width (jagged, crawling slowly). */
    private static float[] edge(float k, float t, long seed) {
        float s = seed * 0.37F;
        float shape = (float) Math.pow(Math.max(Mth.sin(k * Mth.PI), 0.0F), 0.7);
        float mid = 0.07F * Mth.sin(k * 9.0F + s) + 0.04F * Mth.sin(k * 23.0F - s * 1.7F) + 0.015F * Mth.sin(k * 41.0F + t * 0.05F);
        float jag = 1.0F + 0.25F * Mth.sin(k * 31.0F + t * 0.07F + s) + 0.15F * Mth.sin(k * 57.0F - t * 0.11F);
        float breathe = 1.0F + 0.08F * Mth.sin(t * 0.04F + s);
        return new float[] {mid, WIDTH * shape * jag * breathe};
    }

    @Override
    public void render(SkyHolderBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        SkyblockType type = be.sky();
        float t = (be.getLevel().getGameTime() % 240000L) + partialTick;
        long seed = be.getBlockPos().asLong();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = cam.x - (be.getBlockPos().getX() + 0.5), dz = cam.z - (be.getBlockPos().getZ() + 0.5);
        ps.pushPose();
        ps.translate(0.5F, 0.0F, 0.5F);
        ps.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
        Matrix4f m = ps.last().pose();
        float base = (16.0F - HEIGHT * 16.0F) / 32.0F + 0.15F;
        // ---- the sky through the crack ----
        int n = 0;
        float[] prev = null;
        for (int i = 0; i <= STEPS; i++) {
            float k = i / (float) STEPS;
            float[] e = edge(k, t, seed);
            float y = base + k * HEIGHT;
            float[] cur = {e[0] - e[1], e[0] + e[1], y};
            if (prev != null && type != null) {
                n = SkyQuads.quad(quads, n, at(m, prev[0], prev[2]), at(m, cur[0], cur[2]), at(m, cur[1], cur[2]), at(m, prev[1], prev[2]));
            }
            prev = cur;
        }
        if (type != null) {
            SkyQuads.draw(type, quads, n, buffers);
        }
        // ---- its glowing edges, and a dark lip just inside them ----
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        prev = null;
        for (int i = 0; i <= STEPS; i++) {
            float k = i / (float) STEPS;
            float[] e = edge(k, t, seed);
            float y = base + k * HEIGHT;
            float pulse = 0.75F + 0.25F * Mth.sin(t * 0.15F + k * 12.0F);
            float[] cur = {e[0] - e[1], e[0] + e[1], y, pulse};
            if (prev != null) {
                for (int side = 0; side < 2; side++) {
                    float sgn = side == 0 ? -1.0F : 1.0F;
                    float x0 = prev[side], x1 = cur[side];
                    float w0 = 0.05F + 0.04F * prev[3], w1 = 0.05F + 0.04F * cur[3];
                    ribbon(glow, m, x0, prev[2], x1, cur[2], sgn * w0, sgn * w1, prev[3], cur[3]);
                }
            }
            prev = cur;
        }
        // ---- shards of the edge, drifting round it ----
        for (int i = 0; i < 6; i++) {
            float a = t * 0.02F * (i % 2 == 0 ? 1 : -1) + i * 1.05F + seed % 7;
            float y = base + HEIGHT * (0.15F + 0.7F * ((i * 0.37F + t * 0.002F) % 1.0F));
            float r = 0.28F + 0.08F * Mth.sin(t * 0.03F + i);
            float x = Mth.cos(a) * r, z = Mth.sin(a) * r * 0.4F;
            float s = 0.025F + 0.012F * (i % 3);
            float spin = t * 0.08F + i;
            float cx = Mth.cos(spin) * s, sx = Mth.sin(spin) * s;
            int alpha = 200;
            float[][] pts = {{x - cx, y - sx}, {x - sx, y + cx}, {x + cx, y + sx}, {x + sx, y - cx}};
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < 4; k++) {
                    float[] p = pts[pass == 0 ? k : 3 - k];
                    put(glow, m, p[0], p[1], z, k < 2 ? 230 : 180, k < 2 ? 215 : 140, 255, alpha);
                }
            }
        }
        ps.popPose();
    }

    /** A strip of glow along one edge piece, fading outwards. */
    private static void ribbon(VertexConsumer c, Matrix4f m, float x0, float y0, float x1, float y1, float w0, float w1, float p0, float p1) {
        int a0 = (int) (230 * p0), a1 = (int) (230 * p1);
        // (inner: bright white-violet at the edge; outer: fading to nothing)
        put(c, m, x0 - w0 * 0.35F, y0, 0.0F, 255, 255, 255, a0);
        put(c, m, x1 - w1 * 0.35F, y1, 0.0F, 255, 255, 255, a1);
        put(c, m, x1 + w1, y1, 0.0F, 150, 90, 255, 0);
        put(c, m, x0 + w0, y0, 0.0F, 150, 90, 255, 0);
        put(c, m, x0 + w0, y0, 0.0F, 150, 90, 255, 0);
        put(c, m, x1 + w1, y1, 0.0F, 150, 90, 255, 0);
        put(c, m, x1 - w1 * 0.35F, y1, 0.0F, 255, 255, 255, a1);
        put(c, m, x0 - w0 * 0.35F, y0, 0.0F, 255, 255, 255, a0);
    }

    private static void put(VertexConsumer c, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
        c.addVertex(m, x, y, z).setColor(r, g, b, a);
    }

    private static float[] at(Matrix4f m, float x, float y) {
        Vector3f v = m.transformPosition(x, y, 0.0F, new Vector3f());
        return new float[] {v.x, v.y, v.z};
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SkyHolderBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.6, 0.0, 0.6).expandTowards(0, 1.0, 0);
    }
}
