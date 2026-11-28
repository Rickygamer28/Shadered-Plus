package com.example.skyblocks.client;

import com.example.skyblocks.GamingGearBlock;
import com.example.skyblocks.GamingGearBlockEntity;
import com.example.skyblocks.client.tv.TvPlayers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Gaming gear's lights: the tower's three front fans (rings turning through the colours, each a little behind the
 * last), the glow inside behind its glass side and the strip along its graphics card; the keyboard's wave of colour
 * across its keys. With music playing near, they brighten with it - the bass in the fans and the inside, each key
 * column with its own band (low at the left, high at the right).
 */
public class GamingGearRenderer implements BlockEntityRenderer<GamingGearBlockEntity> {
    /** (The render test: as if music played.) */
    public static boolean testMusic;

    public GamingGearRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GamingGearBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState st = be.getBlockState();
        if (!(st.getBlock() instanceof GamingGearBlock gear) || be.getLevel() == null) return;
        float t = (be.getLevel().getGameTime() % 72000 + pt) / 20.0F;
        float[] bands = TvPlayers.bandsNear(be.getBlockPos().getCenter(), 24);
        if (testMusic) {
            bands = new float[16];
            for (int i = 0; i < 16; i++) bands[i] = 0.5F + 0.5F * Mth.sin(t * 3 + i * 0.7F);
        }
        float bass = Math.max(bands[0], Math.max(bands[1], bands[2]));
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-st.getValue(GamingGearBlock.FACING).toYRot() + 180.0F)); // (its front to the north)
        pose.translate(-0.5F, 0.0F, -0.5F);
        Matrix4f m = pose.last().pose();
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        if (gear.keyboard) {
            keys(glow, m, t, bands);
        } else {
            tower(glow, m, t, bass, bands);
        }
        pose.popPose();
    }

    private static final float PX = 1.0F / 16;

    private static void tower(VertexConsumer v, Matrix4f m, float t, float bass, float[] bands) {
        float strength = 0.45F + 0.55F * bass;
        // three fans down its front (at z 2, just before it), turning through the colours
        for (int f = 0; f < 3; f++) {
            float cx = 8 * PX, cy = (11.5F - f * 4) * PX, z = 2 * PX - 0.002F;
            int c = hue(t * 0.15F + f * 0.12F, strength);
            ring(v, m, cx, cy, z, 1.75F * PX, 1.25F * PX, c, -1);
            // (a faint glow over the whole fan, its blades)
            disc(v, m, cx, cy, z + 0.0005F, 1.25F * PX, hue(t * 0.15F + f * 0.12F, strength * 0.25F), -1);
        }
        // inside, through its glass side (the east, as it's turned): a wash of colour, brighter on the beat
        float gx = 13 * PX + 0.002F; // (over its glass: lighting what is behind it)
        int inside = hue(t * 0.1F + 0.5F, 0.18F + 0.35F * bass);
        quadX(v, m, gx, 1 * PX, 2.5F * PX, 14 * PX, 13.5F * PX, inside);
        // the graphics card's strip (a line along it, its colours running)
        for (int i = 0; i < 8; i++) {
            float z0 = (3 + i) * PX, z1 = (4 + i) * PX;
            int c = hue(t * 0.3F + i * 0.06F, 0.5F + 0.5F * bands[Math.min(15, 2 + i)]);
            quadX(v, m, gx + 0.0005F, 6.2F * PX, z0, 6.8F * PX, z1, c);
        }
        // its power light
        int p = hue(t * 0.05F, 0.9F);
        quadZ(v, m, 7.6F * PX, 14.0F * PX, 8.4F * PX, 14.4F * PX, 2 * PX - 0.003F, p);
    }

    private static void keys(VertexConsumer v, Matrix4f m, float t, float[] bands) {
        // its keys: 14 columns by 5 rows over the top of the keyboard (x 1.5 to 11.5, z 4.5 to 10.5), a wave of colour
        // across them, each column as bright as its band
        float y = 1.0F * PX + 0.002F;
        int cols = 14, rows = 5;
        float x0 = 1.5F, x1 = 11.5F, z0 = 4.6F, z1 = 10.4F;
        float kw = (x1 - x0) / cols, kd = (z1 - z0) / rows;
        for (int c = 0; c < cols; c++) {
            float band = bands[Math.min(15, c * 16 / cols)];
            for (int r = 0; r < rows; r++) {
                int col = hue(t * 0.25F - c * 0.045F - r * 0.02F, 0.35F + 0.65F * band);
                float ax = (x0 + c * kw + kw * 0.18F) * PX, bx = (x0 + (c + 1) * kw - kw * 0.18F) * PX;
                float az = (z0 + r * kd + kd * 0.2F) * PX, bz = (z0 + (r + 1) * kd - kd * 0.2F) * PX;
                quadY(v, m, ax, az, bx, bz, y, col);
            }
        }
        // the mouse's light, and its pad's edge
        int mc = hue(t * 0.25F - 0.7F, 0.5F + 0.5F * bands[8]);
        quadY(v, m, 13.6F * PX, 6.4F * PX, 14.4F * PX, 6.9F * PX, 1.25F * PX + 0.002F, mc);
        quadY(v, m, 12.3F * PX, 10.6F * PX, 15.7F * PX, 10.8F * PX, 0.25F * PX + 0.002F, mc);
    }

    /** A colour round the wheel (0-1, wrapping) at this brightness, as light (no alpha: it's added). */
    private static int hue(float h, float bright) {
        h = h - Mth.floor(h);
        int rgb = Mth.hsvToRgb(h, 0.85F, 1.0F);
        bright = Mth.clamp(bright, 0, 1);
        int r = (int) ((rgb >> 16 & 255) * bright), g = (int) ((rgb >> 8 & 255) * bright), b = (int) ((rgb & 255) * bright);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** A ring in the plane z (facing north), from radius ri to ro. */
    private static void ring(VertexConsumer v, Matrix4f m, float cx, float cy, float z, float ro, float ri, int c, int unused) {
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            float a0 = i * Mth.TWO_PI / seg, a1 = (i + 1) * Mth.TWO_PI / seg;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            vtx(v, m, cx + c0 * ro, cy + s0 * ro, z, c);
            vtx(v, m, cx + c1 * ro, cy + s1 * ro, z, c);
            vtx(v, m, cx + c1 * ri, cy + s1 * ri, z, c);
            vtx(v, m, cx + c0 * ri, cy + s0 * ri, z, c);
        }
    }

    private static void disc(VertexConsumer v, Matrix4f m, float cx, float cy, float z, float r, int c, int unused) {
        int seg = 12;
        for (int i = 0; i < seg; i++) {
            float a0 = i * Mth.TWO_PI / seg, a1 = (i + 1) * Mth.TWO_PI / seg;
            vtx(v, m, cx, cy, z, c);
            vtx(v, m, cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, z, c);
            vtx(v, m, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, z, c);
            vtx(v, m, cx, cy, z, c);
        }
    }

    /** Upright, facing north, at depth z. */
    private static void quadZ(VertexConsumer v, Matrix4f m, float xa, float ya, float xb, float yb, float z, int c) {
        vtx(v, m, xa, ya, z, c);
        vtx(v, m, xb, ya, z, c);
        vtx(v, m, xb, yb, z, c);
        vtx(v, m, xa, yb, z, c);
    }

    /** Upright, across x = const. */
    private static void quadX(VertexConsumer v, Matrix4f m, float x, float ya, float za, float yb, float zb, int c) {
        vtx(v, m, x, ya, za, c);
        vtx(v, m, x, ya, zb, c);
        vtx(v, m, x, yb, zb, c);
        vtx(v, m, x, yb, za, c);
    }

    /** Level, at height y. */
    private static void quadY(VertexConsumer v, Matrix4f m, float xa, float za, float xb, float zb, float y, int c) {
        vtx(v, m, xa, y, za, c);
        vtx(v, m, xa, y, zb, c);
        vtx(v, m, xb, y, zb, c);
        vtx(v, m, xb, y, za, c);
    }

    private static final float[] QUAD = new float[12];
    private static final int[] QUAD_C = new int[4];
    private static int held;

    /** One corner of a quad; each quad drawn from both sides once its fourth comes (light is seen from either). */
    private static void vtx(VertexConsumer v, Matrix4f m, float x, float y, float z, int c) {
        org.joml.Vector3f p = m.transformPosition(x, y, z, new org.joml.Vector3f());
        QUAD[held * 3] = p.x;
        QUAD[held * 3 + 1] = p.y;
        QUAD[held * 3 + 2] = p.z;
        QUAD_C[held] = c;
        if (++held < 4) return;
        held = 0;
        for (int i = 0; i < 4; i++) v.addVertex(QUAD[i * 3], QUAD[i * 3 + 1], QUAD[i * 3 + 2]).setColor(QUAD_C[i]);
        for (int i = 3; i >= 0; i--) v.addVertex(QUAD[i * 3], QUAD[i * 3 + 1], QUAD[i * 3 + 2]).setColor(QUAD_C[i]);
    }
}
