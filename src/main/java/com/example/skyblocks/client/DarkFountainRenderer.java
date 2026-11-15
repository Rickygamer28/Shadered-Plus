package com.example.skyblocks.client;

import com.example.skyblocks.DarkFountainBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * A dark fountain (DarkFountainBlockEntity): the tear in the ground, jagged, opening onto a starry dark (Shadered's
 * space sky, as a window: SkyWindow) with ink spilt round it; then the column of darkness pouring up out of it -
 * rippling and twisting as it rises, wider at its foot where it bursts out, billowing at its top - a window into the
 * same dark, its edges lit pale like the rim of a wave.
 */
public class DarkFountainRenderer implements BlockEntityRenderer<DarkFountainBlockEntity> {
    private static final String SKY = "SPACE";
    /** Points round the tear, round the column; how tall each ring of the column is (blocks). */
    private static final int TEAR = 30, AROUND = 22;
    private static final float RING = 0.8F;
    private final SkyWindow.Quads quads = new SkyWindow.Quads();

    public DarkFountainRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(DarkFountainBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        RenderType type = SkyWindow.type(SKY, "NORMAL");
        if (type == null) {
            return;
        }
        float t = level.getGameTime() + partialTick;
        float tear = be.tear(t), column = be.column(t);
        if (tear <= 0.0F && column <= 0.0F) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        long seed = pos.asLong() * 0x9E3779B97F4A7C15L;
        Matrix4f m = pose.last().pose();
        quads.clear();
        // ---- the tear: a jagged hole in the ground ----
        float open = 1.0F - (1.0F - tear) * (1.0F - tear);
        float[] rx = new float[TEAR], rz = new float[TEAR];
        for (int i = 0; i < TEAR; i++) {
            double a = i * Math.PI * 2 / TEAR;
            // (jagged: long splits and short ones, the same for this tear each time)
            float jag = (float) (((seed >>> (i % 60)) & 7) / 7.0);
            float r = open * 1.55F * (0.62F + 0.5F * jag * jag + 0.08F * Mth.sin(t * 0.07F + i));
            rx[i] = 0.5F + (float) Math.cos(a) * r;
            rz[i] = 0.5F + (float) Math.sin(a) * r * 0.8F;
        }
        float y = 0.012F;
        for (int i = 0; i < TEAR; i++) {
            int j = (i + 1) % TEAR;
            quads.add(0.5F, y, 0.5F, rx[i], y, rz[i], rx[j], y, rz[j], rx[j], y, rz[j]);
        }
        // ---- the column ----
        float height = column * DarkFountainBlockEntity.HEIGHT;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float cx = (float) (pos.getX() + 0.5 - cam.x), cy = (float) (pos.getY() - cam.y), cz = (float) (pos.getZ() + 0.5 - cam.z);
        int rings = height > 0.05F ? Math.max(2, (int) Math.ceil(height / RING) + 1) : 0;
        float[][] ringX = new float[rings][AROUND], ringY = new float[rings][AROUND], ringZ = new float[rings][AROUND];
        for (int k = 0; k < rings; k++) {
            float f = k / (float) (rings - 1);
            float h = f * height;
            for (int i = 0; i < AROUND; i++) {
                float a = (float) (i * Math.PI * 2 / AROUND) + h * 0.11F - t * 0.04F;
                float r = radius(h, height, a, t);
                // (the very top rounds over into a billowing cap)
                float top = Mth.clamp((h - (height - 2.5F)) / 2.5F, 0.0F, 1.0F);
                float yy = h + top * 0.8F * Mth.sin(a * 3 + t * 0.15F) * 0.4F;
                ringX[k][i] = 0.5F + Mth.cos(a) * r;
                ringY[k][i] = yy;
                ringZ[k][i] = 0.5F + Mth.sin(a) * r;
            }
        }
        for (int k = 0; k + 1 < rings; k++) {
            for (int i = 0; i < AROUND; i++) {
                int j = (i + 1) % AROUND;
                quads.add(ringX[k][i], ringY[k][i], ringZ[k][i], ringX[k][j], ringY[k][j], ringZ[k][j], ringX[k + 1][j], ringY[k + 1][j], ringZ[k + 1][j],
                        ringX[k + 1][i], ringY[k + 1][i], ringZ[k + 1][i]);
            }
        }
        if (rings > 1) {
            // (its top closed over)
            int k = rings - 1;
            float tx = 0.5F, ty = ringY[k][0] + 0.9F * column, tz = 0.5F;
            for (int i = 0; i < AROUND; i++) {
                int j = (i + 1) % AROUND;
                quads.add(ringX[k][i], ringY[k][i], ringZ[k][i], ringX[k][j], ringY[k][j], ringZ[k][j], tx, ty, tz, tx, ty, tz);
            }
        }
        SkyWindow.draw(buffers, type, m, quads.data, quads.count);

        // ---- light: the rim of the column (bright where it turns away from you), ink round the tear ----
        VertexConsumer glow = buffers.getBuffer(GlowTypes.GLOW);
        for (int k = 0; k + 1 < rings; k++) {
            for (int i = 0; i < AROUND; i++) {
                int j = (i + 1) % AROUND;
                rim(glow, m, ringX[k][i], ringY[k][i], ringZ[k][i], cx, cy, cz, column);
                rim(glow, m, ringX[k][j], ringY[k][j], ringZ[k][j], cx, cy, cz, column);
                rim(glow, m, ringX[k + 1][j], ringY[k + 1][j], ringZ[k + 1][j], cx, cy, cz, column);
                rim(glow, m, ringX[k + 1][i], ringY[k + 1][i], ringZ[k + 1][i], cx, cy, cz, column);
            }
        }
        VertexConsumer ink = buffers.getBuffer(GlowTypes.SHADE);
        for (int i = 0; i < TEAR; i++) {
            int j = (i + 1) % TEAR;
            float s = 1.0F + 0.45F * open;
            ink.addVertex(m, rx[i], 0.008F, rz[i]).setColor(0.02F, 0.0F, 0.05F, 0.95F * tear);
            ink.addVertex(m, 0.5F + (rx[i] - 0.5F) * s, 0.008F, 0.5F + (rz[i] - 0.5F) * s).setColor(0.0F, 0.0F, 0.0F, 0.0F);
            ink.addVertex(m, 0.5F + (rx[j] - 0.5F) * s, 0.008F, 0.5F + (rz[j] - 0.5F) * s).setColor(0.0F, 0.0F, 0.0F, 0.0F);
            ink.addVertex(m, rx[j], 0.008F, rz[j]).setColor(0.02F, 0.0F, 0.05F, 0.95F * tear);
        }
        // (the tear's edge glowing faintly)
        for (int i = 0; i < TEAR; i++) {
            int j = (i + 1) % TEAR;
            float in = 0.88F;
            glow.addVertex(m, rx[i], 0.02F, rz[i]).setColor(0.75F, 0.65F, 1.0F, 0.5F * tear);
            glow.addVertex(m, 0.5F + (rx[i] - 0.5F) * in, 0.02F, 0.5F + (rz[i] - 0.5F) * in).setColor(0.75F, 0.65F, 1.0F, 0.0F);
            glow.addVertex(m, 0.5F + (rx[j] - 0.5F) * in, 0.02F, 0.5F + (rz[j] - 0.5F) * in).setColor(0.75F, 0.65F, 1.0F, 0.0F);
            glow.addVertex(m, rx[j], 0.02F, rz[j]).setColor(0.75F, 0.65F, 1.0F, 0.5F * tear);
        }
    }

    /** The column's radius at height h (of `height`), round at angle a: rippling, wide at its foot, billowing at its top. */
    private static float radius(float h, float height, float a, float t) {
        float r = DarkFountainBlockEntity.RADIUS;
        r *= 1.0F + 0.45F * (float) Math.exp(-h * 1.1F); // (bursting out of the ground)
        r *= 1.0F + 0.07F * Mth.sin(3 * a + h * 0.9F - t * 0.25F) + 0.05F * Mth.sin(5 * a - h * 1.7F + t * 0.4F) + 0.04F * Mth.sin(h * 0.6F - t * 0.3F);
        float top = Mth.clamp((h - (height - 3.0F)) / 3.0F, 0.0F, 1.0F);
        r *= 1.0F + 0.55F * Mth.sin(top * Mth.PI * 0.8F); // (billowing out at the top)
        return r;
    }

    private static void rim(VertexConsumer g, Matrix4f m, float x, float y, float z, float cx, float cy, float cz, float column) {
        // its normal (out from the middle, level), and the way to it from the eye: bright edge-on
        float nx = x - 0.5F, nz = z - 0.5F, nl = Mth.sqrt(nx * nx + nz * nz) + 1.0E-5F;
        float vx = cx + x - 0.5F, vy = cy + y, vz = cz + z - 0.5F, vl = Mth.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-5F;
        float facing = Math.abs((nx * vx + nz * vz) / (nl * vl));
        float edge = (1.0F - facing);
        float a = edge * edge * edge * 0.65F * column;
        float r = 1.02F;
        g.addVertex(m, 0.5F + (x - 0.5F) * r, y, 0.5F + (z - 0.5F) * r).setColor(0.72F, 0.68F, 1.0F, a);
    }

    @Override
    public boolean shouldRenderOffScreen(DarkFountainBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(DarkFountainBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 3, p.getY() - 1, p.getZ() - 3, p.getX() + 4, p.getY() + DarkFountainBlockEntity.HEIGHT + 4, p.getZ() + 4);
    }
}
