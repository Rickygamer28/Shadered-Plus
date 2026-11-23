package com.example.skyblocks.client;

import com.example.skyblocks.SkyHolderBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The fountains: a jet leaping from the spout and falling back in eight arcs into the pool. Each stream is a ribbon
 * turned to face you along its length. The Sky Fountain's streams and pool are windows into its sky (SkyWindow), with
 * a soft glow along the streams' edges; the Water Fountain's are HD water (shaders/core/hd_water): the pool rippling
 * and reflecting, foaming where the arcs land, the streams rushing down streaked white.
 */
public class FountainRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    static final int ARCS = 8, STEPS = 12;
    static final float SURFACE = 5.2F / 16.0F, SPOUT = 11.0F / 16.0F, LAND = 0.32F;
    private final boolean sky;
    private final SkyWindow.Quads quads = new SkyWindow.Quads();

    public FountainRenderer(BlockEntityRendererProvider.Context context, boolean sky) {
        this.sky = sky;
    }

    public static <T extends BlockEntity> BlockEntityRendererProvider<T> of(boolean sky) {
        return c -> new FountainRenderer<>(c, sky);
    }

    @Override
    public void render(T be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (!(be.getLevel() instanceof ClientLevel level)) {
            return;
        }
        float t = (level.getGameTime() % 240000L) + partialTick;
        Vec3 c = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vector3f cam = new Vector3f((float) (c.x - be.getBlockPos().getX()), (float) (c.y - be.getBlockPos().getY()), (float) (c.z - be.getBlockPos().getZ()));
        Matrix4f m = ps.last().pose();
        float peak = 1.72F + 0.05F * Mth.sin(t * 0.21F) + 0.02F * Mth.sin(t * 0.73F);
        // ---- the streams: the jet, then the arcs ----
        int n = STEPS + 1;
        float[][] jet = new float[n][];
        for (int i = 0; i < n; i++) {
            float s = i / (float) STEPS;
            float wob = 0.008F * Mth.sin(t * 0.9F + s * 9.0F);
            jet[i] = new float[] {0.5F + wob, Mth.lerp(s, SPOUT, peak), 0.5F - wob, 0.045F - 0.012F * s};
        }
        float[][][] arcs = new float[ARCS][n][];
        for (int k = 0; k < ARCS; k++) {
            float a = k * Mth.TWO_PI / ARCS + 0.2F;
            float reach = LAND * (1.0F + 0.04F * Mth.sin(t * 0.3F + k * 1.7F));
            for (int i = 0; i < n; i++) {
                float s = i / (float) STEPS;
                float r = reach * s;
                arcs[k][i] = new float[] {0.5F + Mth.cos(a) * r, peak - (peak - SURFACE) * s * s, 0.5F + Mth.sin(a) * r, 0.03F + 0.008F * s};
            }
        }
        if (sky) {
            SkyblockType type = be instanceof SkyHolderBlockEntity h ? h.sky() : null;
            RenderType window = type == null ? null : SkyWindow.type(type.name(), "NORMAL");
            if (window == null) {
                return;
            }
            quads.clear();
            float i0 = 2.0F / 16.0F, i1 = 14.0F / 16.0F;
            quads.add(i0, SURFACE, i0, i1, SURFACE, i0, i1, SURFACE, i1, i0, SURFACE, i1);
            ribbonSky(jet, cam);
            for (float[][] arc : arcs) {
                ribbonSky(arc, cam);
            }
            SkyWindow.draw(buffers, window, m, quads.data, quads.count);
            VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
            glowEdges(glow, m, jet, cam);
            for (float[][] arc : arcs) {
                glowEdges(glow, m, arc, cam);
            }
            return;
        }
        if (FxShaders.water == null) {
            return;
        }
        float day = Mth.clamp((level.getSkyDarken(partialTick) - 0.2F) / 0.8F, 0.0F, 1.0F) * Math.max(LightTexture.sky(light) / 15.0F, 0.3F);
        int d = (int) (day * 255);
        VertexConsumer vc = buffers.getBuffer(FxShaders.Types.WATER);
        // ---- the pool: a grid, its foam where the arcs land and round the spout ----
        int g = 6;
        float i0 = 2.0F / 16.0F, size = 12.0F / 16.0F;
        float bx = be.getBlockPos().getX(), bz = be.getBlockPos().getZ();
        for (int x = 0; x < g; x++) {
            for (int z = 0; z < g; z++) {
                float x0 = i0 + size * x / g, x1 = i0 + size * (x + 1) / g, z0 = i0 + size * z / g, z1 = i0 + size * (z + 1) / g;
                pool(vc, m, x0, z0, bx, bz, d);
                pool(vc, m, x1, z0, bx, bz, d);
                pool(vc, m, x1, z1, bx, bz, d);
                pool(vc, m, x0, z1, bx, bz, d);
            }
        }
        ribbonWater(vc, m, jet, cam, d);
        for (float[][] arc : arcs) {
            ribbonWater(vc, m, arc, cam, d);
        }
    }

    private static void pool(VertexConsumer vc, Matrix4f m, float x, float z, float bx, float bz, int day) {
        float r = (float) Math.hypot(x - 0.5F, z - 0.5F);
        float foam = Math.max(Mth.clamp(1.0F - Math.abs(r - LAND) * 9.0F, 0.0F, 1.0F), Mth.clamp(1.0F - (r - 0.12F) * 10.0F, 0.0F, 1.0F) * 0.6F);
        vc.addVertex(m, x, SURFACE, z).setUv(bx + x, bz + z).setColor(0, (int) (foam * 255), day, 255);
    }

    /** A stream's sides at each point: a ribbon turned to face the camera, its half-width point[3]. */
    private static float[][] sides(float[][] p, Vector3f cam) {
        int n = p.length;
        float[][] out = new float[n][6];
        Vector3f tan = new Vector3f(), view = new Vector3f(), side = new Vector3f();
        for (int i = 0; i < n; i++) {
            float[] a = p[Math.max(0, i - 1)], b = p[Math.min(n - 1, i + 1)];
            tan.set(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
            view.set(cam.x - p[i][0], cam.y - p[i][1], cam.z - p[i][2]);
            tan.cross(view, side);
            if (side.lengthSquared() < 1.0E-8F) {
                side.set(1, 0, 0);
            }
            side.normalize(p[i][3]);
            out[i][0] = p[i][0] - side.x;
            out[i][1] = p[i][1] - side.y;
            out[i][2] = p[i][2] - side.z;
            out[i][3] = p[i][0] + side.x;
            out[i][4] = p[i][1] + side.y;
            out[i][5] = p[i][2] + side.z;
        }
        return out;
    }

    private void ribbonSky(float[][] p, Vector3f cam) {
        float[][] s = sides(p, cam);
        for (int i = 0; i + 1 < s.length; i++) {
            quads.add(s[i][0], s[i][1], s[i][2], s[i][3], s[i][4], s[i][5], s[i + 1][3], s[i + 1][4], s[i + 1][5], s[i + 1][0], s[i + 1][1], s[i + 1][2]);
        }
    }

    private static void ribbonWater(VertexConsumer vc, Matrix4f m, float[][] p, Vector3f cam, int day) {
        float[][] s = sides(p, cam);
        float along = 0.0F;
        for (int i = 0; i + 1 < s.length; i++) {
            float next = along + (float) Math.sqrt(sq(p[i + 1][0] - p[i][0]) + sq(p[i + 1][1] - p[i][1]) + sq(p[i + 1][2] - p[i][2]));
            vc.addVertex(m, s[i][0], s[i][1], s[i][2]).setUv(along, -1).setColor(255, 0, day, 255);
            vc.addVertex(m, s[i][3], s[i][4], s[i][5]).setUv(along, 1).setColor(255, 0, day, 255);
            vc.addVertex(m, s[i + 1][3], s[i + 1][4], s[i + 1][5]).setUv(next, 1).setColor(255, 0, day, 255);
            vc.addVertex(m, s[i + 1][0], s[i + 1][1], s[i + 1][2]).setUv(next, -1).setColor(255, 0, day, 255);
            along = next;
        }
    }

    /** A soft glow just outside a sky stream's edges. */
    private static void glowEdges(VertexConsumer vc, Matrix4f m, float[][] p, Vector3f cam) {
        float[][] wide = new float[p.length][];
        for (int i = 0; i < p.length; i++) {
            wide[i] = new float[] {p[i][0], p[i][1], p[i][2], p[i][3] * 1.9F};
        }
        float[][] in = sides(p, cam), out = sides(wide, cam);
        for (int side = 0; side < 2; side++) {
            int o = side * 3;
            for (int i = 0; i + 1 < in.length; i++) {
                for (int pass = 0; pass < 2; pass++) {
                    int a = pass == 0 ? i : i + 1, b = pass == 0 ? i + 1 : i;
                    vc.addVertex(m, in[a][o], in[a][o + 1], in[a][o + 2]).setColor(235, 240, 255, 110);
                    vc.addVertex(m, in[b][o], in[b][o + 1], in[b][o + 2]).setColor(235, 240, 255, 110);
                    vc.addVertex(m, out[b][o], out[b][o + 1], out[b][o + 2]).setColor(200, 210, 255, 0);
                    vc.addVertex(m, out[a][o], out[a][o + 1], out[a][o + 2]).setColor(200, 210, 255, 0);
                }
            }
        }
    }

    private static float sq(float x) {
        return x * x;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(T be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0, 1.0, 0);
    }
}
