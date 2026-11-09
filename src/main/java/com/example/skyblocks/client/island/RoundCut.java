package com.example.skyblocks.client.island;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An island cut round (the weather globe's): every face cut to the circle (a many-sided one, so it's smooth), and where
 * a solid block is cut, its cut side closed with that block's texture - a round slice of the island, like a cake.
 */
final class RoundCut {
    private static final int SIDES = 96, INTS = IslandMesh.QuadCollector.INTS;

    private RoundCut() {}

    private static float[] corner(IslandView island, int k) {
        double a = k * Math.PI * 2.0 / SIDES;
        return new float[] {island.cutX + (float) Math.cos(a) * island.cutR, island.cutZ + (float) Math.sin(a) * island.cutR};
    }

    /** The quads in {@code d} (vertex after vertex, four to a quad), each cut to the circle. */
    static int[] clip(IslandView island, int[] d) {
        if (island.cutR <= 0.0F) {
            return d;
        }
        float inner = island.cutR * (float) Math.cos(Math.PI / SIDES); // (well inside every edge of the many-sided circle)
        List<int[]> out = new ArrayList<>(d.length / INTS);
        for (int q = 0; q + 4 * INTS <= d.length; q += 4 * INTS) {
            boolean allIn = true;
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int v = 0; v < 4; v++) {
                float x = Float.intBitsToFloat(d[q + v * INTS]) - island.cutX, z = Float.intBitsToFloat(d[q + v * INTS + 2]) - island.cutZ;
                allIn &= x * x + z * z <= inner * inner;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
            if (allIn) {
                for (int v = 0; v < 4; v++) {
                    out.add(java.util.Arrays.copyOfRange(d, q + v * INTS, q + v * INTS + INTS));
                }
                continue;
            }
            float nx = Math.max(Math.max(minX, -maxX), 0.0F), nz = Math.max(Math.max(minZ, -maxZ), 0.0F);
            if (nx * nx + nz * nz >= island.cutR * island.cutR) {
                continue; // (all outside)
            }
            List<float[]> poly = new ArrayList<>(8);
            for (int v = 0; v < 4; v++) {
                poly.add(unpack(d, q + v * INTS));
            }
            for (int k = 0; k < SIDES && poly.size() >= 3; k++) {
                poly = clipEdge(poly, corner(island, k), corner(island, k + 1), island);
            }
            if (poly.size() < 3) {
                continue;
            }
            for (int i = 1; i + 1 < poly.size(); i++) { // (as quads: a fan, each triangle with its last corner twice)
                out.add(pack(poly.get(0)));
                out.add(pack(poly.get(i)));
                out.add(pack(poly.get(i + 1)));
                out.add(pack(poly.get(i + 1)));
            }
        }
        int[] r = new int[out.size() * INTS];
        for (int i = 0; i < out.size(); i++) {
            System.arraycopy(out.get(i), 0, r, i * INTS, INTS);
        }
        return r;
    }

    // a vertex unpacked: x y z, colour a r g b, u v, light block sky, normal (packed, kept)
    private static float[] unpack(int[] d, int i) {
        int c = d[i + 3], l = d[i + 6];
        return new float[] {Float.intBitsToFloat(d[i]), Float.intBitsToFloat(d[i + 1]), Float.intBitsToFloat(d[i + 2]),
                (c >>> 24) & 255, (c >> 16) & 255, (c >> 8) & 255, c & 255,
                Float.intBitsToFloat(d[i + 4]), Float.intBitsToFloat(d[i + 5]), l & 0xFFFF, (l >>> 16) & 0xFFFF, Float.intBitsToFloat(d[i + 7])};
    }

    private static int[] pack(float[] v) {
        int c = (Math.round(v[3]) & 255) << 24 | (Math.round(v[4]) & 255) << 16 | (Math.round(v[5]) & 255) << 8 | (Math.round(v[6]) & 255);
        int l = (Math.round(v[9]) & 0xFFFF) | (Math.round(v[10]) & 0xFFFF) << 16;
        return new int[] {Float.floatToRawIntBits(v[0]), Float.floatToRawIntBits(v[1]), Float.floatToRawIntBits(v[2]), c,
                Float.floatToRawIntBits(v[7]), Float.floatToRawIntBits(v[8]), l, Float.floatToRawIntBits(v[11])};
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        float[] r = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            r[i] = a[i] + (b[i] - a[i]) * t;
        }
        r[11] = a[11]; // (the normal: packed, not blended)
        return r;
    }

    /** Keeps the part of the polygon on the circle's side of the edge p -> q. */
    private static List<float[]> clipEdge(List<float[]> poly, float[] p, float[] q, IslandView island) {
        // (inside: the same side as the middle)
        float ex = q[0] - p[0], ez = q[1] - p[1];
        float mid = ex * (island.cutZ - p[1]) - ez * (island.cutX - p[0]);
        List<float[]> out = new ArrayList<>(poly.size() + 2);
        for (int i = 0; i < poly.size(); i++) {
            float[] a = poly.get(i), b = poly.get((i + 1) % poly.size());
            float sa = (ex * (a[2] - p[1]) - ez * (a[0] - p[0])) * Math.signum(mid);
            float sb = (ex * (b[2] - p[1]) - ez * (b[0] - p[0])) * Math.signum(mid);
            if (sa >= 0) {
                out.add(a);
            }
            if ((sa >= 0) != (sb >= 0)) {
                out.add(lerp(a, b, sa / (sa - sb)));
            }
        }
        return out;
    }

    /** Where a solid block is cut, its cut side: the circle's edges inside its square, its texture on them. */
    static void wall(BlockRenderDispatcher blocks, IslandView island, IslandMesh.QuadCollector collector, BlockState state, BlockPos pos) {
        if (collector == null || !state.isSolidRender(island, pos)) {
            return;
        }
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        // (cut at all: a corner of its square outside the circle)
        boolean cut = false;
        for (int c = 0; c < 4; c++) {
            float dx = x + (c & 1) - island.cutX, dz = z + (c >> 1) - island.cutZ;
            cut |= dx * dx + dz * dz > island.cutR * island.cutR * Math.cos(Math.PI / SIDES) * Math.cos(Math.PI / SIDES);
        }
        if (!cut) {
            return;
        }
        BakedModel model = blocks.getBlockModel(state);
        List<BakedQuad> quads;
        try {
            quads = model.getQuads(state, Direction.NORTH, RandomSource.create(state.getSeed(pos)), net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
        } catch (RuntimeException e) {
            quads = List.of();
        }
        TextureAtlasSprite sprite = quads.isEmpty() ? model.getParticleIcon() : quads.get(0).getSprite();
        int light = island.lightAt(x + 0.5, y + 1.5, z + 0.5) | 0x00F00000; // (as lit as the open air above it)
        for (int k = 0; k < SIDES; k++) {
            float[] a = corner(island, k), b = corner(island, k + 1);
            // the edge's part inside the block's square (Liang-Barsky)
            float t0 = 0.0F, t1 = 1.0F, ex = b[0] - a[0], ez = b[1] - a[1];
            float[][] tests = {{-ex, a[0] - x}, {ex, x + 1 - a[0]}, {-ez, a[1] - z}, {ez, z + 1 - a[1]}};
            boolean in = true;
            for (float[] t : tests) {
                if (t[0] == 0.0F) {
                    if (t[1] < 0.0F) {
                        in = false;
                    }
                } else {
                    float r = t[1] / t[0];
                    if (t[0] < 0.0F) {
                        t0 = Math.max(t0, r);
                    } else {
                        t1 = Math.min(t1, r);
                    }
                }
            }
            if (!in || t1 - t0 <= 1.0E-4F) {
                continue;
            }
            float ax = a[0] + ex * t0, az = a[1] + ez * t0, bx = a[0] + ex * t1, bz = a[1] + ez * t1;
            float nx = (ax + bx) / 2 - island.cutX, nz = (az + bz) / 2 - island.cutZ;
            float len = (float) Math.sqrt(nx * nx + nz * nz);
            nx /= len;
            nz /= len;
            if (-ez * nx + ex * nz < 0.0F) { // (facing out: the corners the other way round)
                float tx = ax, tz = az;
                ax = bx;
                az = bz;
                bx = tx;
                bz = tz;
            }
            boolean alongX = Math.abs(bx - ax) > Math.abs(bz - az);
            float ua = sprite.getU(alongX ? ax - x : az - z), ub = sprite.getU(alongX ? bx - x : bz - z);
            int normal = (((int) (nx * 127.0F)) & 0xFF) | (((int) (nz * 127.0F)) & 0xFF) << 16;
            int colour = 0xFFCCCCCC;
            collector.put(new int[] {f(ax), f(y), f(az), colour, f(ua), f(sprite.getV(1.0F)), light, normal});
            collector.put(new int[] {f(bx), f(y), f(bz), colour, f(ub), f(sprite.getV(1.0F)), light, normal});
            collector.put(new int[] {f(bx), f(y + 1), f(bz), colour, f(ub), f(sprite.getV(0.0F)), light, normal});
            collector.put(new int[] {f(ax), f(y + 1), f(az), colour, f(ua), f(sprite.getV(0.0F)), light, normal});
        }
    }

    private static int f(float v) {
        return Float.floatToRawIntBits(v);
    }
}
