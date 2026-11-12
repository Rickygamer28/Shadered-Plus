package com.example.skyblocks.client;

import com.example.skyblocks.MapHeights;
import com.example.skyblocks.MapTableBlock;
import com.example.skyblocks.MapTableBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;

/**
 * The land a map table's map shows, as a little model standing on the table: each patch of the map a column as high as
 * the land there (heights from MapHeights), coloured as the map colours it; the sea a clear blue sheet over its floor;
 * a red pin where the table is, if it's on the map. Tables side by side are one big table: the map spreads over the
 * biggest square of them (finer, the bigger it is), drawn by the first of them that holds a map.
 */
public class MapTableRenderer implements BlockEntityRenderer<MapTableBlockEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/misc/white.png");

    /** The square of tables a map spreads over (corner, side in blocks), and the table that draws it. */
    record Square(int x0, int z0, int side, long drawer) {}

    private static final Map<Long, Square> SQUARES = new HashMap<>();
    private static long squaresAt;

    /** The land, built for one table's map at one size. */
    static final class Mesh {
        int version = -1, side;
        final List<float[]> solid = new ArrayList<>(), water = new ArrayList<>();
    }

    private static final Map<Long, Mesh> MESHES = new HashMap<>();

    public MapTableRenderer(BlockEntityRendererProvider.Context context) {}

    static Square square(Level level, BlockPos start) {
        long now = System.nanoTime();
        if (now - squaresAt > 1_000_000_000L) {
            SQUARES.clear();
            squaresAt = now;
        }
        Square known = SQUARES.get(start.asLong());
        if (known != null) {
            return known;
        }
        List<BlockPos> members = new ArrayList<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(start));
        Set<Long> seen = new HashSet<>(List.of(start.asLong()));
        int x0 = start.getX(), x1 = x0, z0 = start.getZ(), z1 = z0;
        while (!todo.isEmpty() && members.size() < 256) {
            BlockPos q = todo.poll();
            members.add(q);
            x0 = Math.min(x0, q.getX());
            x1 = Math.max(x1, q.getX());
            z0 = Math.min(z0, q.getZ());
            z1 = Math.max(z1, q.getZ());
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = q.relative(d);
                if (seen.add(n.asLong()) && level.getBlockState(n).getBlock() instanceof MapTableBlock) {
                    todo.add(n);
                }
            }
        }
        int w = x1 - x0 + 1, h = z1 - z0 + 1;
        // (the first table, by place, holding a map with something to show)
        members.sort((a, b) -> a.getX() != b.getX() ? Integer.compare(a.getX(), b.getX()) : Integer.compare(a.getZ(), b.getZ()));
        long drawer = Long.MIN_VALUE;
        for (BlockPos q : members) {
            if (level.getBlockEntity(q) instanceof MapTableBlockEntity be && be.colors != null) {
                drawer = q.asLong();
                break;
            }
        }
        boolean whole = members.size() == w * h && members.size() < 256;
        for (BlockPos q : members) {
            Square s;
            if (whole) {
                int side = Math.min(w, h);
                s = new Square(x0 + (w - side) / 2, z0 + (h - side) / 2, side, drawer);
            } else {
                s = new Square(q.getX(), q.getZ(), 1, q.asLong()); // (not a rectangle: each table on its own)
            }
            SQUARES.put(q.asLong(), s);
        }
        return SQUARES.get(start.asLong());
    }

    @Override
    public void render(MapTableBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        if (level == null || be.colors == null) {
            return;
        }
        Square sq = square(level, pos);
        if (sq.drawer != pos.asLong()) {
            return;
        }
        Mesh mesh = MESHES.get(pos.asLong());
        if (mesh == null || mesh.version != be.version || mesh.side != sq.side) {
            mesh = build(be, sq.side, pos, sq);
            MESHES.put(pos.asLong(), mesh);
            if (MESHES.size() > 64) {
                MESHES.keySet().removeIf(k -> !(level.getBlockEntity(BlockPos.of(k)) instanceof MapTableBlockEntity));
            }
        }
        poseStack.pushPose();
        poseStack.translate(sq.x0 - pos.getX(), 1.0F, sq.z0 - pos.getZ());
        PoseStack.Pose pose = poseStack.last();
        emit(buffers.getBuffer(RenderType.entitySolid(WHITE)), pose, mesh.solid, light, overlay);
        if (!mesh.water.isEmpty()) {
            emit(buffers.getBuffer(RenderType.entityTranslucent(WHITE)), pose, mesh.water, light, overlay);
        }
        poseStack.popPose();
    }

    /** Each quad: 4 corners (x y z), then its colour (ARGB, as a float's bits) and normal (x y z). */
    private static void emit(VertexConsumer vc, PoseStack.Pose pose, List<float[]> quads, int light, int overlay) {
        for (float[] q : quads) {
            int argb = Float.floatToRawIntBits(q[12]);
            for (int i = 0; i < 4; i++) {
                vc.addVertex(pose, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).setColor(argb).setUv(0.5F, 0.5F).setOverlay(overlay).setLight(light)
                        .setNormal(pose, q[13], q[14], q[15]);
            }
        }
    }

    private static final float[] EXAGGERATE = {2.0F, 2.4F, 2.8F, 3.2F, 3.6F};

    static Mesh build(MapTableBlockEntity be, int side, BlockPos tablePos, Square sq) {
        Mesh mesh = new Mesh();
        mesh.version = be.version;
        mesh.side = side;
        int res = side >= 2 ? 128 : 64, k = 128 / res;
        int n = res * res;
        int[] colour = new int[n];
        boolean[] seen = new boolean[n], water = new boolean[n];
        float[] top = new float[n], depth = new float[n];
        int[] votes = new int[64];
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int cz = 0; cz < res; cz++) {
            for (int cx = 0; cx < res; cx++) {
                java.util.Arrays.fill(votes, 0);
                int filled = 0, wet = 0, heights = 0;
                float sum = 0, dsum = 0;
                for (int pz = cz * k; pz < cz * k + k; pz++) {
                    for (int px = cx * k; px < cx * k + k; px++) {
                        int i = pz * 128 + px;
                        int id = (be.colors[i] & 255) >> 2;
                        if (id == 0) {
                            continue;
                        }
                        filled++;
                        votes[id & 63]++;
                        if (id == MapColor.WATER.id) {
                            wet++;
                        }

                    }
                }
                int c = cz * res + cx;
                if (filled * 2 < k * k || filled == 0) {
                    continue;
                }
                seen[c] = true;
                int best = 0;
                for (int v = 1; v < 64; v++) {
                    if (votes[v] > votes[best]) {
                        best = v;
                    }
                }
                colour[c] = best;
                water[c] = wet * 2 >= filled;
                // (its height: the pixels of its own colour - a wall's top, not the wall averaged with the grass beside it)
                for (int pz = cz * k; pz < cz * k + k; pz++) {
                    for (int px = cx * k; px < cx * k + k; px++) {
                        int i = pz * 128 + px;
                        if (((be.colors[i] & 255) >> 2) != best) {
                            continue;
                        }
                        int t = (short) ((be.top[i * 2] & 255) << 8 | be.top[i * 2 + 1] & 255);
                        if (t != MapHeights.UNKNOWN) {
                            heights++;
                            sum += t;
                            dsum += be.depth[i] & 255;
                        }
                    }
                }
                top[c] = heights > 0 ? Math.round(sum / heights) : Float.NaN; // (whole blocks: flat land stays flat)
                depth[c] = heights > 0 ? Math.round(dsum / heights) : 0;
                if (heights > 0) {
                    float floor = water[c] ? top[c] - Math.max(1, depth[c]) : top[c];
                    lo = Math.min(lo, floor);
                    hi = Math.max(hi, top[c]);
                }
            }
        }
        if (lo == Float.MAX_VALUE) {
            lo = 62;
            hi = 64;
        }
        // heights to the model: the land's own scale across (a little taller for maps of more land), not too tall a model
        float base = lo - 3;
        int step = 1 << Math.min(be.scale, 4);
        float vs = side / (128.0F * step) * EXAGGERATE[Math.min(be.scale, 4)];
        float cap = 0.55F * side;
        if ((hi - base) * vs > cap) {
            vs = cap / (hi - base);
        }
        float plinth = 0.04F;
        float cell = (float) side / res;
        float[] land = new float[n], surface = new float[n];
        for (int c = 0; c < n; c++) {
            if (!seen[c]) {
                continue;
            }
            float t = Float.isNaN(top[c]) ? lo + 2 : top[c];
            surface[c] = plinth + (t - base) * vs;
            land[c] = water[c] ? plinth + (t - Math.max(1, depth[c]) - base) * vs : surface[c];
        }
        // tops: runs of the same height and colour along each row, one quad
        for (int cz = 0; cz < res; cz++) {
            int cx = 0;
            while (cx < res) {
                int c = cz * res + cx;
                if (!seen[c]) {
                    cx++;
                    continue;
                }
                int col = topColour(colour[c], water[c], depth[c], land[c] / Math.max(0.01F, cap));
                int end = cx + 1;
                while (end < res) {
                    int d = cz * res + end;
                    if (!seen[d] || land[d] != land[c] || topColour(colour[d], water[d], depth[d], land[d] / Math.max(0.01F, cap)) != col) {
                        break;
                    }
                    end++;
                }
                float y = land[c];
                mesh.solid.add(quad(cx * cell, y, cz * cell, cx * cell, y, (cz + 1) * cell, end * cell, y, (cz + 1) * cell, end * cell, y, cz * cell,
                        col, 0, 1, 0));
                cx = end;
            }
        }
        // the sea: one sheet per run of water at the same level
        for (int cz = 0; cz < res; cz++) {
            int cx = 0;
            while (cx < res) {
                int c = cz * res + cx;
                if (!seen[c] || !water[c]) {
                    cx++;
                    continue;
                }
                int end = cx + 1;
                while (end < res && seen[cz * res + end] && water[cz * res + end] && surface[cz * res + end] == surface[c]) {
                    end++;
                }
                float y = surface[c];
                int col = 0xA0000000 | 0x3F76E4;
                mesh.water.add(quad(cx * cell, y, cz * cell, cx * cell, y, (cz + 1) * cell, end * cell, y, (cz + 1) * cell, end * cell, y, cz * cell,
                        col, 0, 1, 0));
                cx = end;
            }
        }
        // sides: wherever the next patch is lower (or there is none: down to the table)
        int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int cz = 0; cz < res; cz++) {
            for (int cx = 0; cx < res; cx++) {
                int c = cz * res + cx;
                if (!seen[c]) {
                    continue;
                }
                for (int[] d : dirs) {
                    int nx = cx + d[0], nz = cz + d[1];
                    boolean inside = nx >= 0 && nz >= 0 && nx < res && nz < res && seen[nz * res + nx];
                    float below = inside ? land[nz * res + nx] : 0.0F;
                    if (below < land[c]) {
                        side(mesh.solid, cx, cz, d, cell, below, land[c], sideColour(colour[c], water[c]));
                    }
                    if (water[c]) {
                        float nTop = inside ? (water[nz * res + nx] ? surface[nz * res + nx] : land[nz * res + nx]) : 0.0F;
                        float from = Math.max(nTop, land[c]);
                        if (from < surface[c]) {
                            side(mesh.water, cx, cz, d, cell, from, surface[c], 0x90000000 | 0x3060C0);
                        }
                    }
                }
            }
        }
        pin(mesh, be, tablePos, res, cell, land, seen, side);
        return mesh;
    }

    /** A red pin where the table itself is, if that's on the map. */
    private static void pin(Mesh mesh, MapTableBlockEntity be, BlockPos tablePos, int res, float cell, float[] land, boolean[] seen, int side) {
        int step = 1 << Math.min(be.scale, 4);
        int px = (int) Math.floor((tablePos.getX() + 0.5 - be.centerX) / step) + 64, pz = (int) Math.floor((tablePos.getZ() + 0.5 - be.centerZ) / step) + 64;
        if (px < 0 || pz < 0 || px > 127 || pz > 127) {
            return;
        }
        int cx = px * res / 128, cz = pz * res / 128, c = cz * res + cx;
        float y0 = seen[c] ? land[c] : 0.0F;
        float x = (cx + 0.5F) * cell, z = (cz + 0.5F) * cell;
        float s = 0.006F * (float) Math.sqrt(side), h = 0.1F * (float) Math.sqrt(side), r = 0.018F * (float) Math.sqrt(side);
        box(mesh.solid, x - s, y0, z - s, x + s, y0 + h, z + s, 0xFFDDDDDD);
        box(mesh.solid, x - r, y0 + h, z - r, x + r, y0 + h + 2 * r, z + r, 0xFFE02020);
    }

    private static void box(List<float[]> out, float x0, float y0, float z0, float x1, float y1, float z1, int col) {
        int dark = shade(col, 0.8F);
        out.add(quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, col, 0, 1, 0));
        // (each side wound anticlockwise seen from outside, as the solid sheet culls the backs)
        out.add(quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, dark, 0, 0, -1));
        out.add(quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, dark, 0, 0, 1));
        out.add(quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, dark, -1, 0, 0));
        out.add(quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, dark, 1, 0, 0));
    }

    /** The side of patch (cx, cz) facing d, from y0 up to y1. */
    private static void side(List<float[]> out, int cx, int cz, int[] d, float cell, float y0, float y1, int col) {
        float a = cx * cell, b = (cx + 1) * cell, c = cz * cell, e = (cz + 1) * cell;
        if (d[1] < 0) {
            out.add(quad(b, y0, c, a, y0, c, a, y1, c, b, y1, c, col, 0, 0, -1));
        } else if (d[1] > 0) {
            out.add(quad(a, y0, e, b, y0, e, b, y1, e, a, y1, e, col, 0, 0, 1));
        } else if (d[0] < 0) {
            out.add(quad(a, y0, c, a, y0, e, a, y1, e, a, y1, c, col, -1, 0, 0));
        } else {
            out.add(quad(b, y0, e, b, y0, c, b, y1, c, b, y1, e, col, 1, 0, 0));
        }
    }

    private static float[] quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                                int argb, float nx, float ny, float nz) {
        return new float[] {x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, Float.intBitsToFloat(argb), nx, ny, nz};
    }

    /** A patch's top: the map's colour for it (a touch softer), lighter the higher it is; the sea floor sandy, darker the deeper. */
    private static int topColour(int id, boolean water, float depth, float height) {
        if (water) {
            float t = Math.min(1.0F, depth / 14.0F);
            return 0xFF000000 | mix(0xC9B98A, 0x2E4560, t);
        }
        int c = MapColor.byId(id).col;
        c = mix(c, 0x9A9A8A, 0.12F);
        int band = Math.min(3, (int) (height * 4)); // (a few bands, so flat land stays one quad)
        return 0xFF000000 | shade(c, 0.9F + band * 0.05F);
    }

    /** A patch's sides: earthy, a shade of its top. */
    private static int sideColour(int id, boolean water) {
        int c = water ? 0xA89A70 : mix(MapColor.byId(id).col, 0x7A6248, 0.55F);
        return 0xFF000000 | shade(c, 0.78F);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static int shade(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * f)), g = Math.min(255, (int) (((c >> 8) & 255) * f)), b = Math.min(255, (int) ((c & 255) * f));
        return (c & 0xFF000000) | r << 16 | g << 8 | b;
    }

    @Override
    public AABB getRenderBoundingBox(MapTableBlockEntity be) {
        BlockPos p = be.getBlockPos();
        if (be.getLevel() == null) {
            return new AABB(p).expandTowards(0, 1, 0);
        }
        Square sq = square(be.getLevel(), p);
        return new AABB(sq.x0, p.getY() + 1, sq.z0, sq.x0 + sq.side, p.getY() + 1.2 + sq.side * 0.6, sq.z0 + sq.side);
    }

    @Override
    public boolean shouldRenderOffScreen(MapTableBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
