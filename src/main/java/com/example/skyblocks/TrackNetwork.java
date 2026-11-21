package com.example.skyblocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A world's smooth train track (built with the Track Tool, TrackToolItem): nodes - a point and the way the track runs
 * through it (level) - joined by pieces, each a smooth curve from one node to another (a cubic Bezier: leaving the one
 * along its way, arriving at the other along its; rising or falling between them gently, level at each end). A node's
 * two sides each hold up to two pieces: two, it's a switch - which one a train takes, set by hand or by redstone under it.
 * Saved with the world; the server's copy sent whole to its players when it changes (TrackPayloads), the client's
 * drawn (client/TrackRenderer). Trains run along it (TrainRoute).
 */
public class TrackNetwork extends SavedData {
    /** The tightest turn (radius, blocks), the steepest slope (rise per block along), the longest and shortest piece. */
    public static final float MIN_RADIUS = 7.0F, MAX_GRADE = 0.36F, MAX_LENGTH = 64.0F, MIN_LENGTH = 2.0F;
    /** How far apart the points along a piece are (blocks). */
    public static final float STEP = 0.5F;

    public static final class Node {
        public final int id;
        public final Vec3 pos;
        /** The way the track runs through it (level, unit). */
        public final Vec3 dir;
        /** Its pieces on each side: [0] towards -dir, [1] towards +dir. */
        public final List<List<Integer>> sides = List.of(new ArrayList<>(), new ArrayList<>());
        /** Which piece a switch side sends trains along (index into its side's list). */
        public final int[] pick = {0, 0};
        /** (server) Whether redstone was on under it, last look. */
        public boolean powered;

        Node(int id, Vec3 pos, Vec3 dir) {
            this.id = id;
            this.pos = pos;
            this.dir = new Vec3(dir.x, 0, dir.z).normalize();
        }

        public List<Integer> side(int sign) {
            return sides.get(sign > 0 ? 1 : 0);
        }

        public boolean isSwitch() {
            return sides.get(0).size() > 1 || sides.get(1).size() > 1;
        }

        /** Its switch side (+1, -1), or 0. */
        public int switchSide() {
            return sides.get(1).size() > 1 ? 1 : sides.get(0).size() > 1 ? -1 : 0;
        }

        /** The piece a train leaving by that side takes. */
        public int taken(int sign) {
            List<Integer> s = side(sign);
            return s.isEmpty() ? -1 : s.get(Math.min(pick[sign > 0 ? 1 : 0], s.size() - 1));
        }
    }

    public static final class Seg {
        public final int id, a, aSide, b, bSide;
        /** Its points along it (from a to b), every STEP or so, the way it runs at each, and how far along each is. */
        public Vec3[] pts, tan;
        public float[] cum;

        Seg(int id, int a, int aSide, int b, int bSide) {
            this.id = id;
            this.a = a;
            this.aSide = aSide;
            this.b = b;
            this.bSide = bSide;
        }

        public float length() {
            return cum[cum.length - 1];
        }

        /** The node at an end (0 a, 1 b), and the side of it it's on. */
        public int node(int end) {
            return end == 0 ? a : b;
        }

        public int sideAt(int end) {
            return end == 0 ? aSide : bSide;
        }
    }

    public final Map<Integer, Node> nodes = new LinkedHashMap<>();
    public final Map<Integer, Seg> segs = new LinkedHashMap<>();
    private int nextId = 1;
    /** Bumped on every change (the client's drawing rebuilt). */
    public int version;

    // ---- where it's kept ----

    /** The client's copy (its world's), sent by the server. */
    public static TrackNetwork client = new TrackNetwork();

    public static TrackNetwork of(Level level) {
        if (level instanceof ServerLevel sl) {
            return sl.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TrackNetwork::new, TrackNetwork::load, null), "skyblocks_tracks");
        }
        return client;
    }

    // ---- shape ----

    /** A piece's curve from p0 leaving along d0 to p3 arriving along d1: its four control points. */
    public static Vec3[] controls(Vec3 p0, Vec3 d0, Vec3 p3, Vec3 d1) {
        Vec3 chord = new Vec3(p3.x - p0.x, 0, p3.z - p0.z);
        double D = chord.length();
        double cos = Mth.clamp(d0.dot(d1), -1.0, 1.0);
        double theta = Math.acos(cos);
        double k;
        if (theta < 1e-3) {
            k = D / 3.0;
        } else {
            double r = D / (2.0 * Math.sin(theta / 2.0));
            k = 4.0 / 3.0 * Math.tan(theta / 4.0) * r;
        }
        k = Mth.clamp(k, D * 0.2, D * 0.6);
        Vec3 p1 = new Vec3(p0.x + d0.x * k, p0.y, p0.z + d0.z * k);
        Vec3 p2 = new Vec3(p3.x - d1.x * k, p3.y, p3.z - d1.z * k);
        return new Vec3[] {p0, p1, p2, p3};
    }

    static Vec3 bez(Vec3[] c, double t) {
        double u = 1 - t;
        return c[0].scale(u * u * u).add(c[1].scale(3 * u * u * t)).add(c[2].scale(3 * u * t * t)).add(c[3].scale(t * t * t));
    }

    static Vec3 bez1(Vec3[] c, double t) {
        double u = 1 - t;
        return c[1].subtract(c[0]).scale(3 * u * u).add(c[2].subtract(c[1]).scale(6 * u * t)).add(c[3].subtract(c[2]).scale(3 * t * t));
    }

    static Vec3 bez2(Vec3[] c, double t) {
        return c[2].subtract(c[1].scale(2)).add(c[0]).scale(6 * (1 - t)).add(c[3].subtract(c[2].scale(2)).add(c[1]).scale(6 * t));
    }

    /** A planned piece: its points (every STEP), the way at each, how far along each; or why it can't be. */
    public record Plan(Vec3[] pts, Vec3[] tan, float[] cum, @Nullable String problem) {
        public float length() {
            return cum.length == 0 ? 0.0F : cum[cum.length - 1];
        }

        /** Train Rails it takes: one for every two blocks. */
        public int cost() {
            return Math.max(1, Mth.ceil(length() / 2.0F));
        }
    }

    public static Plan plan(Vec3 p0, Vec3 d0, Vec3 p3, Vec3 d1) {
        Vec3[] c = controls(p0, d0, p3, d1);
        int n = 256;
        Vec3[] raw = new Vec3[n + 1];
        double[] rc = new double[n + 1];
        String problem = null;
        double flat = Math.hypot(p3.x - p0.x, p3.z - p0.z);
        if (flat < MIN_LENGTH) {
            problem = "short";
        } else if (flat > MAX_LENGTH) {
            problem = "long";
        } else if (new Vec3(p3.x - p0.x, 0, p3.z - p0.z).normalize().dot(d0) < 0.05 || new Vec3(p3.x - p0.x, 0, p3.z - p0.z).normalize().dot(d1) < 0.05) {
            problem = "backwards";
        }
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            raw[i] = bez(c, t);
            if (i > 0) {
                rc[i] = rc[i - 1] + raw[i].distanceTo(raw[i - 1]);
            }
            if (problem == null) {
                Vec3 v = bez1(c, t), a = bez2(c, t);
                double hv = Math.hypot(v.x, v.z);
                double curv = Math.abs(v.x * a.z - v.z * a.x) / Math.max(hv * hv * hv, 1e-9);
                if (curv > 1.0 / MIN_RADIUS) {
                    problem = "tight";
                } else if (Math.abs(v.y) / Math.max(hv, 1e-9) > MAX_GRADE) {
                    problem = "steep";
                }
            }
        }
        double total = rc[n];
        int m = Math.max(2, (int) Math.ceil(total / STEP)) + 1;
        Vec3[] pts = new Vec3[m], tan = new Vec3[m];
        float[] cum = new float[m];
        int j = 0;
        for (int i = 0; i < m; i++) {
            double s = total * i / (m - 1);
            while (j < n - 1 && rc[j + 1] < s) {
                j++;
            }
            double f = (s - rc[j]) / Math.max(rc[j + 1] - rc[j], 1e-9);
            double t = (j + f) / n;
            pts[i] = bez(c, t);
            tan[i] = bez1(c, t).normalize();
            cum[i] = (float) s;
        }
        return new Plan(pts, tan, cum, problem);
    }

    /** The way a piece leaves a node by a side. */
    public static Vec3 out(Node n, int side) {
        return n.dir.scale(side);
    }

    private void shape(Seg s) {
        Node a = nodes.get(s.a), b = nodes.get(s.b);
        Plan p = plan(a.pos, out(a, s.aSide), b.pos, out(b, s.bSide).scale(-1));
        s.pts = p.pts();
        s.tan = p.tan();
        s.cum = p.cum();
    }

    // ---- finding things ----

    /** The nearest point on a piece to a place (within reach): the piece, the point's index; or null. */
    public record Near(Seg seg, int index, double dist) {}

    @Nullable
    public Near nearest(Vec3 p, double reach) {
        Near best = null;
        double bd = reach * reach;
        for (Seg s : segs.values()) {
            Vec3 lo = s.pts[0], hi = s.pts[s.pts.length - 1];
            double span = s.length() / 2.0 + reach + 1.0;
            if (p.distanceToSqr(lo.add(hi).scale(0.5)) > span * span + 64.0) {
                continue;
            }
            for (int i = 0; i < s.pts.length; i++) {
                double d = s.pts[i].distanceToSqr(p);
                if (d < bd) {
                    bd = d;
                    best = new Near(s, i, Math.sqrt(d));
                }
            }
        }
        return best;
    }

    /** The nearest node to a place (within reach), or null. */
    @Nullable
    public Node nearestNode(Vec3 p, double reach) {
        Node best = null;
        double bd = reach * reach;
        for (Node n : nodes.values()) {
            double d = n.pos.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = n;
            }
        }
        return best;
    }

    // ---- changing it (the server) ----

    private Node addNode(Vec3 pos, Vec3 dir) {
        Node n = new Node(nextId++, pos, dir);
        nodes.put(n.id, n);
        return n;
    }

    private Seg addSeg(Node a, int aSide, Node b, int bSide) {
        Seg s = new Seg(nextId++, a.id, aSide, b.id, bSide);
        segs.put(s.id, s);
        a.side(aSide).add(s.id);
        b.side(bSide).add(s.id);
        shape(s);
        return s;
    }

    /** A piece cut in two at a point along it: a node there (the way it runs there). */
    public Node split(Seg s, int index) {
        Node a = nodes.get(s.a), b = nodes.get(s.b);
        Vec3 t = s.tan[index];
        Node mid = addNode(s.pts[index], new Vec3(t.x, 0, t.z));
        removeSeg(s);
        // (its ends kept, though cutting it left them bare for a moment)
        nodes.putIfAbsent(a.id, a);
        nodes.putIfAbsent(b.id, b);
        addSeg(a, s.aSide, mid, -1);
        addSeg(mid, 1, b, s.bSide);
        return mid;
    }

    /** One end of a piece to build: a node there already (and the side), or a new one at a place, running a way. */
    public record End(@Nullable Node node, int side, Vec3 pos, Vec3 dir) {}

    /** Builds a piece between two ends (planned already, fit). */
    public void build(End from, End to) {
        Node a = from.node() != null ? from.node() : addNode(from.pos(), from.dir());
        Node b = to.node() != null ? to.node() : addNode(to.pos(), to.dir());
        int aSide = from.node() != null ? from.side() : 1;
        int bSide = to.node() != null ? to.side() : -1;
        addSeg(a, aSide, b, bSide);
        changed();
    }

    public void removeSeg(Seg s) {
        segs.remove(s.id);
        for (int end = 0; end < 2; end++) {
            Node n = nodes.get(s.node(end));
            if (n != null) {
                n.side(s.sideAt(end)).remove((Integer) s.id);
                if (n.sides.get(0).isEmpty() && n.sides.get(1).isEmpty()) {
                    nodes.remove(n.id);
                }
            }
        }
    }

    public void remove(Seg s) {
        removeSeg(s);
        changed();
    }

    public void toggle(Node n) {
        int side = n.switchSide();
        if (side != 0) {
            int i = side > 0 ? 1 : 0;
            n.pick[i] = (n.pick[i] + 1) % n.side(side).size();
            changed();
        }
    }

    public void changed() {
        version++;
        setDirty();
    }

    // ---- saved, and sent ----

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        ListTag ns = new ListTag();
        for (Node n : nodes.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", n.id);
            t.putDouble("x", n.pos.x);
            t.putDouble("y", n.pos.y);
            t.putDouble("z", n.pos.z);
            t.putFloat("dx", (float) n.dir.x);
            t.putFloat("dz", (float) n.dir.z);
            t.putIntArray("pick", n.pick.clone());
            ns.add(t);
        }
        ListTag ss = new ListTag();
        for (Seg s : segs.values()) {
            ss.add(new IntArrayTag(new int[] {s.id, s.a, s.aSide, s.b, s.bSide}));
        }
        tag.put("nodes", ns);
        tag.put("segs", ss);
        tag.putInt("next", nextId);
        tag.putInt("version", version);
        return tag;
    }

    public static TrackNetwork read(CompoundTag tag) {
        TrackNetwork net = new TrackNetwork();
        for (Tag e : tag.getList("nodes", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            Node n = new Node(t.getInt("id"), new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z")), new Vec3(t.getFloat("dx"), 0, t.getFloat("dz")));
            int[] pick = t.getIntArray("pick");
            for (int i = 0; i < Math.min(2, pick.length); i++) {
                n.pick[i] = pick[i];
            }
            net.nodes.put(n.id, n);
        }
        for (Tag e : tag.getList("segs", Tag.TAG_INT_ARRAY)) {
            int[] v = ((IntArrayTag) e).getAsIntArray();
            if (v.length < 5 || !net.nodes.containsKey(v[1]) || !net.nodes.containsKey(v[3])) {
                continue;
            }
            Seg s = new Seg(v[0], v[1], v[2], v[3], v[4]);
            net.segs.put(s.id, s);
            net.nodes.get(s.a).side(s.aSide).add(s.id);
            net.nodes.get(s.b).side(s.bSide).add(s.id);
            net.shape(s);
        }
        net.nextId = Math.max(tag.getInt("next"), 1);
        net.version = tag.getInt("version");
        return net;
    }

    private static TrackNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        TrackNetwork net = read(tag);
        return net;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag t = write();
        for (String k : t.getAllKeys()) {
            tag.put(k, t.get(k));
        }
        return tag;
    }

    /** The pieces' ids by their nodes, for walking along it (TrainRoute). */
    public Map<Integer, Seg> segsById() {
        return segs;
    }
}
