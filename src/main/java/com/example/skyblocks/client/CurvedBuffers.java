package com.example.skyblocks.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Drawing onto a wall of televisions that turns corners as if it were flat: what's drawn in its unrolled frame (the
 * first column's: x from 1 at its left down to 1 - n, y up from its bottom, z out of the glass) is cut where one column
 * meets the next, and each piece put onto its own column - so a picture, a song's cover, its words, anything, bends
 * round the corners with the glass.
 */
final class CurvedBuffers implements MultiBufferSource {
    private final MultiBufferSource target;
    private final Matrix4f[] columns;
    private final Map<RenderType, Bender> made = new HashMap<>();

    /** @param columns each column's frame (as TelevisionRenderer.column leaves it: x from 1 to 0 across it) */
    CurvedBuffers(MultiBufferSource target, Matrix4f[] columns) {
        this.target = target;
        this.columns = columns;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        finish(); // (what's waiting goes before another kind's asked for: the game may end the last kind's batch)
        return made.computeIfAbsent(type, t -> new Bender(t));
    }

    /** Everything waiting drawn. */
    void finish() {
        for (Bender b : made.values()) b.flush();
    }

    /** One vertex as it was given (in the unrolled frame), with everything that was set on it. */
    private static final class V {
        float x, y, z, u, v, nx, ny, nz;
        int colour = -1, overlay, light;
        boolean hasColour, hasUv, hasOverlay, hasLight, hasNormal;

        V copy() {
            V c = new V();
            c.x = x; c.y = y; c.z = z; c.u = u; c.v = v; c.nx = nx; c.ny = ny; c.nz = nz;
            c.colour = colour; c.overlay = overlay; c.light = light;
            c.hasColour = hasColour; c.hasUv = hasUv; c.hasOverlay = hasOverlay; c.hasLight = hasLight; c.hasNormal = hasNormal;
            return c;
        }

        /** Between a and b, t of the way. */
        static V lerp(V a, V b, float t) {
            V c = a.copy();
            c.x = a.x + (b.x - a.x) * t;
            c.y = a.y + (b.y - a.y) * t;
            c.z = a.z + (b.z - a.z) * t;
            c.u = a.u + (b.u - a.u) * t;
            c.v = a.v + (b.v - a.v) * t;
            c.nx = a.nx + (b.nx - a.nx) * t;
            c.ny = a.ny + (b.ny - a.ny) * t;
            c.nz = a.nz + (b.nz - a.nz) * t;
            if (a.hasColour) {
                int r = 0;
                for (int s = 0; s < 32; s += 8) {
                    int ca = a.colour >>> s & 0xFF, cb = b.colour >>> s & 0xFF;
                    r |= Math.round(ca + (cb - ca) * t) << s;
                }
                c.colour = r;
            }
            return c;
        }
    }

    private final class Bender implements VertexConsumer {
        private final RenderType type;
        private final List<V> quad = new ArrayList<>(4);
        private V now;

        Bender(RenderType type) {
            this.type = type;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (now != null) {
                quad.add(now);
                if (quad.size() == 4) emit();
            }
            now = new V();
            now.x = x;
            now.y = y;
            now.z = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            now.colour = (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
            now.hasColour = true;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            now.u = u;
            now.v = v;
            now.hasUv = true;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            now.overlay = u & 0xFFFF | (v & 0xFFFF) << 16;
            now.hasOverlay = true;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            now.light = u & 0xFFFF | (v & 0xFFFF) << 16;
            now.hasLight = true;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            now.nx = x;
            now.ny = y;
            now.nz = z;
            now.hasNormal = true;
            return this;
        }

        void flush() {
            if (now != null) {
                quad.add(now);
                now = null;
                if (quad.size() == 4) emit();
            }
            quad.clear();
        }

        /** A quad: cut at each column's edges, each piece onto its column. */
        private void emit() {
            List<V> q = new ArrayList<>(quad);
            quad.clear();
            int n = columns.length;
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (V v : q) {
                float s = 1 - v.x; // (how far along from the wall's left)
                lo = Math.min(lo, s);
                hi = Math.max(hi, s);
            }
            int first = Math.max(0, Math.min(n - 1, (int) Math.floor(lo))), last = Math.max(0, Math.min(n - 1, (int) Math.ceil(hi) - 1));
            VertexConsumer out = null;
            for (int i = first; i <= last; i++) {
                List<V> piece = q;
                if (i > 0) piece = clip(piece, i, true);
                if (i < n - 1) piece = clip(piece, i + 1, false);
                if (piece.size() < 3) continue;
                if (out == null) out = target.getBuffer(type);
                // (as quads: a fan, the last corner doubled where there's one short)
                for (int k = 1; k + 1 < piece.size(); k += 2) {
                    V a = piece.get(0), b = piece.get(k), c = piece.get(k + 1), d = k + 2 < piece.size() ? piece.get(k + 2) : c;
                    put(out, a, i);
                    put(out, b, i);
                    put(out, c, i);
                    put(out, d, i);
                }
            }
        }

        /** The part of a polygon at or past s = edge (keepAbove) or before it. */
        private List<V> clip(List<V> poly, float edge, boolean keepAbove) {
            List<V> out = new ArrayList<>(poly.size() + 2);
            for (int k = 0; k < poly.size(); k++) {
                V a = poly.get(k), b = poly.get((k + 1) % poly.size());
                float sa = 1 - a.x - edge, sb = 1 - b.x - edge;
                if (!keepAbove) {
                    sa = -sa;
                    sb = -sb;
                }
                boolean ina = sa >= 0, inb = sb >= 0;
                if (ina) out.add(a);
                if (ina != inb) out.add(V.lerp(a, b, sa / (sa - sb)));
            }
            return out;
        }

        private final Vector4f p = new Vector4f();

        private void put(VertexConsumer out, V v, int column) {
            columns[column].transform(p.set(v.x + column, v.y, v.z, 1.0F)); // (into its column: x from 1 to 0 across it)
            out.addVertex(p.x, p.y, p.z);
            if (v.hasColour) out.setColor(v.colour);
            if (v.hasUv) out.setUv(v.u, v.v);
            if (v.hasOverlay) out.setUv1(v.overlay & 0xFFFF, v.overlay >>> 16);
            if (v.hasLight) out.setUv2(v.light & 0xFFFF, v.light >>> 16);
            if (v.hasNormal) out.setNormal(v.nx, v.ny, v.nz);
        }
    }
}
