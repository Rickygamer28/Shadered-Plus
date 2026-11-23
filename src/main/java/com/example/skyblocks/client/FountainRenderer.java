package com.example.skyblocks.client;

import com.example.skyblocks.FountainBlock;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SkyHolderBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The fountains (FountainBlock). Every block draws its own part of the pool, inside the rims; the basin's north-west
 * block draws the rest round the basin's middle: the statue (a two-tiered fountain, sized to the basin), the water
 * brimming in its bowl and spilling over its four sides in thin sheets, and a jet leaping from its top and falling in
 * arcs into the pool between the statue and the rim. Streams are ribbons turned to face you along their length.
 * The Sky Fountain's water is windows into its sky (SkyWindow), the streams with a soft glow along their edges; the
 * Water Fountain's is HD water (shaders/core/hd_water) - foaming where the arcs and sheets land.
 */
public class FountainRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    public static final ModelResourceLocation WATER_STATUE = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "block/water_fountain_statue"));
    public static final ModelResourceLocation SKY_STATUE = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "block/sky_fountain_statue"));
    static final int STEPS = 12;
    static final float POOL = 6.0F / 16.0F, BASE = 2.0F / 16.0F;
    /** (each block's basin, looked at again twice a second: time, x0, z0, x1, z1) */
    private static final Map<BlockEntity, long[]> BASINS = new WeakHashMap<>();
    private final boolean sky;
    private final SkyWindow.Quads quads = new SkyWindow.Quads();

    public FountainRenderer(BlockEntityRendererProvider.Context context, boolean sky) {
        this.sky = sky;
    }

    public static <T extends BlockEntity> BlockEntityRendererProvider<T> of(boolean sky) {
        return c -> new FountainRenderer<>(c, sky);
    }

    private static long[] basin(BlockEntity be, ClientLevel level) {
        long[] b = BASINS.get(be);
        long now = level.getGameTime();
        if (b == null || now - b[0] > 10 || now < b[0]) {
            int[] r = FountainBlock.basin(level, be.getBlockPos());
            b = new long[] {now, r[0], r[1], r[2], r[3]};
            BASINS.put(be, b);
        }
        return b;
    }

    @Override
    public void render(T be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (!(be.getLevel() instanceof ClientLevel level)) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof FountainBlock)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        long[] b = basin(be, level);
        int w = (int) (b[3] - b[1] + 1), d = (int) (b[4] - b[2] + 1);
        boolean master = pos.getX() == b[1] && pos.getZ() == b[2];
        float cx = b[1] - pos.getX() + w * 0.5F, cz = b[2] - pos.getZ() + d * 0.5F; // (the basin's middle, from this block)
        float sc = FountainBlock.statue(w, d), land = FountainBlock.landing(w, d);
        float t = (level.getGameTime() % 240000L) + partialTick;
        Matrix4f m = ps.last().pose();
        // ---- this block's part of the pool ----
        float x0 = state.getValue(FountainBlock.WEST) ? 0 : 3 / 16.0F, x1 = state.getValue(FountainBlock.EAST) ? 1 : 13 / 16.0F;
        float z0 = state.getValue(FountainBlock.NORTH) ? 0 : 3 / 16.0F, z1 = state.getValue(FountainBlock.SOUTH) ? 1 : 13 / 16.0F;
        Statue s = master ? new Statue(cx, cz, sc, land, t, w * d > 1 ? 12 : 8) : null;
        if (master) {
            BakedModel model = Minecraft.getInstance().getModelManager().getModel(sky ? SKY_STATUE : WATER_STATUE);
            ps.pushPose();
            ps.translate(cx, BASE, cz);
            ps.scale(sc, sc, sc);
            ps.translate(-0.5F, -BASE, -0.5F);
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), state, model,
                    1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
            ps.popPose();
        }
        Vec3 c = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vector3f cam = new Vector3f((float) (c.x - pos.getX()), (float) (c.y - pos.getY()), (float) (c.z - pos.getZ()));
        if (sky) {
            SkyblockType type = skyOf(level, be, b);
            RenderType window = type == null ? null : SkyWindow.type(type.name(), "NORMAL");
            if (window == null) {
                return;
            }
            quads.clear();
            quads.add(x0, POOL, z0, x1, POOL, z0, x1, POOL, z1, x0, POOL, z1);
            if (s != null) {
                float h = s.bowlIn;
                quads.add(cx - h, s.bowlWater, cz - h, cx + h, s.bowlWater, cz - h, cx + h, s.bowlWater, cz + h, cx - h, s.bowlWater, cz + h);
                ribbonSky(s.jet, cam);
                for (float[][] arc : s.arcs) {
                    ribbonSky(arc, cam);
                }
                for (float[][][] sheet : s.sheets) {
                    for (int i = 0; i + 1 < sheet.length; i++) {
                        float[] p = sheet[i][0], q = sheet[i][1], r = sheet[i + 1][1], u = sheet[i + 1][0];
                        quads.add(p[0], p[1], p[2], q[0], q[1], q[2], r[0], r[1], r[2], u[0], u[1], u[2]);
                    }
                }
            }
            SkyWindow.draw(buffers, window, m, quads.data, quads.count);
            if (s != null) {
                VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
                glowEdges(glow, m, s.jet, cam);
                for (float[][] arc : s.arcs) {
                    glowEdges(glow, m, arc, cam);
                }
            }
            return;
        }
        if (FxShaders.water == null) {
            return;
        }
        float day = Mth.clamp((level.getSkyDarken(partialTick) - 0.2F) / 0.8F, 0.0F, 1.0F) * Math.max(LightTexture.sky(light) / 15.0F, 0.3F);
        int dl = (int) (day * 255);
        VertexConsumer vc = buffers.getBuffer(FxShaders.Types.WATER);
        // (the pool in a grid: its foam where the arcs land, and where the sheets fall round the statue)
        int g = 4;
        float bowlOut = 0.3F * sc;
        for (int i = 0; i < g; i++) {
            for (int j = 0; j < g; j++) {
                float a0 = Mth.lerp(i / (float) g, x0, x1), a1 = Mth.lerp((i + 1) / (float) g, x0, x1);
                float c0 = Mth.lerp(j / (float) g, z0, z1), c1 = Mth.lerp((j + 1) / (float) g, z0, z1);
                pool(vc, m, a0, c0, pos, cx, cz, land, bowlOut, dl);
                pool(vc, m, a1, c0, pos, cx, cz, land, bowlOut, dl);
                pool(vc, m, a1, c1, pos, cx, cz, land, bowlOut, dl);
                pool(vc, m, a0, c1, pos, cx, cz, land, bowlOut, dl);
            }
        }
        if (s != null) {
            float h = s.bowlIn;
            for (float[] k : new float[][] {{-h, -h}, {h, -h}, {h, h}, {-h, h}}) {
                vc.addVertex(m, cx + k[0], s.bowlWater, cz + k[1]).setUv(pos.getX() + cx + k[0], pos.getZ() + cz + k[1]).setColor(0, 60, dl, 255);
            }
            ribbonWater(vc, m, s.jet, cam, dl);
            for (float[][] arc : s.arcs) {
                ribbonWater(vc, m, arc, cam, dl);
            }
            for (float[][][] sheet : s.sheets) {
                float along = 0.0F;
                for (int i = 0; i + 1 < sheet.length; i++) {
                    float next = along + Math.abs(sheet[i][0][1] - sheet[i + 1][0][1]) + 0.02F;
                    for (int k = 0; k < 4; k++) {
                        float[] p = k == 0 ? sheet[i][0] : k == 1 ? sheet[i][1] : k == 2 ? sheet[i + 1][1] : sheet[i + 1][0];
                        vc.addVertex(m, p[0], p[1], p[2]).setUv(k < 2 ? along : next, k == 0 || k == 3 ? -0.3F : 0.3F).setColor(255, 0, dl, 230);
                    }
                    along = next;
                }
            }
        }
    }

    /** The Sky Fountain's sky: its basin's north-west block's (so a basin is all one sky). */
    private static SkyblockType skyOf(ClientLevel level, BlockEntity be, long[] b) {
        BlockEntity master = level.getBlockEntity(new BlockPos((int) b[1], be.getBlockPos().getY(), (int) b[2]));
        if (master instanceof SkyHolderBlockEntity h) {
            return h.sky();
        }
        return be instanceof SkyHolderBlockEntity h ? h.sky() : null;
    }

    /** The statue's water: where everything is (from the block drawing it). */
    private static final class Statue {
        final float bowlIn, bowlWater;
        final float[][] jet;
        final float[][][] arcs;
        final float[][][][] sheets;

        Statue(float cx, float cz, float sc, float land, float t, int count) {
            float spout = BASE + (14.5F / 16.0F - BASE) * sc;
            bowlIn = 3.6F / 16.0F * sc;
            bowlWater = BASE + (10.0F / 16.0F - BASE) * sc;
            float bowlRim = BASE + (10.5F / 16.0F - BASE) * sc, bowlOut = 4.0F / 16.0F * sc + 0.004F;
            float peak = spout + 0.45F + 0.3F * sc + 0.04F * Mth.sin(t * 0.21F) + 0.02F * Mth.sin(t * 0.73F);
            int n = STEPS + 1;
            float thick = 0.03F * (float) Math.sqrt(sc);
            jet = new float[n][];
            for (int i = 0; i < n; i++) {
                float k = i / (float) STEPS, wob = 0.006F * sc * Mth.sin(t * 0.9F + k * 9.0F);
                jet[i] = new float[] {cx + wob, Mth.lerp(k, spout, peak), cz - wob, thick * (1.4F - 0.5F * k)};
            }
            arcs = new float[count][n][];
            for (int a = 0; a < count; a++) {
                float ang = a * Mth.TWO_PI / count + 0.2F;
                float reach = land * (1.0F + 0.03F * Mth.sin(t * 0.3F + a * 1.7F));
                for (int i = 0; i < n; i++) {
                    float k = i / (float) STEPS, r = reach * k;
                    arcs[a][i] = new float[] {cx + Mth.cos(ang) * r, peak - (peak - POOL) * k * k, cz + Mth.sin(ang) * r, thick * (0.7F + 0.3F * k)};
                }
            }
            // (the bowl brimming over: a thin sheet down each side, curving out a little as it falls)
            sheets = new float[4][4][2][];
            float[][] sides = {{-1, -1, 1, -1}, {1, -1, 1, 1}, {1, 1, -1, 1}, {-1, 1, -1, -1}};
            for (int sd = 0; sd < 4; sd++) {
                float[] e = sides[sd];
                float nx = (e[0] + e[2]) * 0.5F, nz = (e[1] + e[3]) * 0.5F; // (outwards)
                for (int i = 0; i < 4; i++) {
                    float k = i / 3.0F;
                    float y = Mth.lerp(k, bowlRim, POOL);
                    float out = bowlOut + 0.06F * sc * k * k;
                    float ax = e[0] * bowlOut, az = e[1] * bowlOut, bx = e[2] * bowlOut, bz = e[3] * bowlOut;
                    sheets[sd][i][0] = new float[] {cx + ax + nx * (out - bowlOut), y, cz + az + nz * (out - bowlOut)};
                    sheets[sd][i][1] = new float[] {cx + bx + nx * (out - bowlOut), y, cz + bz + nz * (out - bowlOut)};
                }
            }
        }
    }

    private static void pool(VertexConsumer vc, Matrix4f m, float x, float z, BlockPos pos, float cx, float cz, float land, float bowlOut, int day) {
        float r = (float) Math.hypot(x - cx, z - cz);
        float foam = Math.max(Mth.clamp(1.0F - Math.abs(r - land) * 7.0F, 0.0F, 1.0F),
                Mth.clamp(1.0F - Math.abs(r - bowlOut * 1.15F) * 8.0F, 0.0F, 1.0F) * 0.8F);
        vc.addVertex(m, x, POOL, z).setUv(pos.getX() + x, pos.getZ() + z).setColor(0, (int) (foam * 255), day, 255);
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
        // (the north-west block draws round the basin's middle: up to the far side of the biggest basin)
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(FountainBlock.MAX - 1, 3.5, FountainBlock.MAX - 1);
    }
}
