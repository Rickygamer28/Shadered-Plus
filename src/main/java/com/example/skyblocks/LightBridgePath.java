package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The way a light bridge goes: from one emitter's front, along straight runs, round the corners its benders make (each
 * a rounded curve of its own radius), to the other emitter's front - up or down between any of them at different heights
 * (a smooth ramp), and in an arch over the middle if it's set to. Worked out the same on the server (which puts its
 * blocks down) and the client (which draws it and carries players along it) from its nodes.
 */
public final class LightBridgePath {
    /** What the bridge is: a walkway, an arch, a moving walkway, a wall of light, a tunnel that carries you through. */
    public static final int BRIDGE = 0, ARCH = 1, SPEED = 2, WALL = 3, TUNNEL = 4, MODES = 5;
    public static final String[] MODE_NAMES = {"bridge", "arch", "speed", "wall", "tunnel"};
    public static final int MAX_CELLS = 64, MAX_BENDS = 8, MAX_RISE = 16;
    /** How steep it may get (blocks up per block along) at its steepest: you're held to its surface as you walk it. */
    public static final float MAX_SLOPE = 1.25F;
    /** Steeper than this, a piece of it isn't a step to climb but a slope you're held onto. */
    public static final float RAMP_SLOPE = 0.04F;
    public static final float STEP = 0.2F;

    /** A corner of the path (block x, y, z: an emitter or a bender) and how round the curve there is (blocks). */
    public record Node(int x, int y, int z, float radius) {}

    /** One point along it: where (world), which way it's heading (unit, flat), how far along (blocks), the plate's bottom. */
    public record Sample(double x, double z, float dx, float dz, float s, double y) {}

    public final List<Node> nodes;
    public final int mode;
    public final List<Sample> samples = new ArrayList<>();
    public float length;
    /** The arch's height in the middle (blocks), after making sure it stays walkable. */
    public float arch;
    public boolean walkable = true;

    /** How wide it is (blocks). */
    public final int width;

    public LightBridgePath(List<Node> nodes, int mode, int width) {
        this.nodes = nodes;
        this.mode = mode;
        this.width = width;
        build();
    }

    // ---- finding it ----

    /**
     * Follows the light from an emitter: straight on, turned by benders, to an emitter facing back along it (up to
     * MAX_RISE blocks higher or lower, as any bender may be). Null if it doesn't get to one.
     */
    @Nullable
    public static List<Node> trace(Level level, BlockPos start, Direction facing) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(start.getX(), start.getY(), start.getZ(), 0));
        Direction d = facing;
        int x = start.getX(), y = start.getY(), z = start.getZ();
        int bends = 0;
        for (int i = 1; i <= MAX_CELLS; i++) {
            x += d.getStepX();
            z += d.getStepZ();
            BlockPos found = null;
            BlockState fs = null;
            for (int k = 0; k <= MAX_RISE * 2 && found == null; k++) {
                int dy = (k + 1) / 2 * (k % 2 == 0 ? -1 : 1);
                BlockPos p = new BlockPos(x, y + dy, z);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof LightBridgeEmitterBlock || s.getBlock() instanceof LightBridgeBenderBlock) {
                    found = p;
                    fs = s;
                }
            }
            if (found == null) {
                continue;
            }
            if (fs.getBlock() instanceof LightBridgeEmitterBlock) {
                if (fs.getValue(LightBridgeEmitterBlock.FACING) != d.getOpposite()) {
                    return null;
                }
                nodes.add(new Node(found.getX(), found.getY(), found.getZ(), 0));
                return nodes;
            }
            // a bender: in one of its two sides and out of the other
            Direction a = fs.getValue(LightBridgeBenderBlock.FACING), b = a.getClockWise();
            Direction from = d.getOpposite();
            Direction out = from == a ? b : from == b ? a : null;
            if (out == null || ++bends > MAX_BENDS) {
                return null;
            }
            nodes.add(new Node(found.getX(), found.getY(), found.getZ(), 0.5F + fs.getValue(LightBridgeBenderBlock.RADIUS)));
            y = found.getY();
            d = out;
        }
        return null;
    }

    // ---- shaping it ----

    private void build() {
        int n = nodes.size();
        if (n < 2) {
            walkable = false;
            return;
        }
        // the polyline: each emitter's front edge, and the benders' middles
        double[] px = new double[n], pz = new double[n], sig = new double[n];
        for (int i = 0; i < n; i++) {
            px[i] = nodes.get(i).x() + 0.5;
            pz[i] = nodes.get(i).z() + 0.5;
        }
        pull(px, pz, 0, 1);
        pull(px, pz, n - 1, n - 2);
        for (int i = 1; i < n; i++) {
            sig[i] = sig[i - 1] + Math.hypot(px[i] - px[i - 1], pz[i] - pz[i - 1]);
        }
        float total = (float) sig[n - 1];
        // how round each corner can be: no more than half of each run either side (all of it next to an emitter)
        float[] r = new float[n];
        for (int i = 1; i < n - 1; i++) {
            double prev = sig[i] - sig[i - 1], next = sig[i + 1] - sig[i];
            r[i] = (float) Math.min(nodes.get(i).radius(), Math.min(i == 1 ? prev : prev / 2, i == n - 2 ? next : next / 2));
        }
        // walk it: straight runs between the curves, quarter circles round them
        float s = 0;
        for (int i = 0; i < n - 1; i++) {
            double ux = Math.signum(px[i + 1] - px[i]), uz = Math.signum(pz[i + 1] - pz[i]);
            double run0 = sig[i] + r[i], run1 = sig[i + 1] - r[i + 1];
            for (double q = run0; q < run1 - 1e-6; q += STEP) {
                double t = q - sig[i];
                add(px[i] + ux * t, pz[i] + uz * t, (float) ux, (float) uz, s, q);
                s += (float) Math.min(STEP, run1 - q);
            }
            if (i + 1 < n - 1) { // the curve round the next corner
                double vx = Math.signum(px[i + 2] - px[i + 1]), vz = Math.signum(pz[i + 2] - pz[i + 1]);
                float ri = r[i + 1];
                double ox = px[i + 1] - ux * ri + vx * ri, oz = pz[i + 1] - uz * ri + vz * ri;
                double arc = Math.PI / 2 * ri;
                int steps = Math.max(2, (int) Math.ceil(arc / STEP));
                for (int k = 0; k < steps; k++) {
                    double th = Math.PI / 2 * k / steps;
                    double cx = ox - vx * ri * Math.cos(th) + ux * ri * Math.sin(th);
                    double cz = oz - vz * ri * Math.cos(th) + uz * ri * Math.sin(th);
                    double hx = vx * Math.sin(th) + ux * Math.cos(th), hz = vz * Math.sin(th) + uz * Math.cos(th);
                    add(cx, cz, (float) hx, (float) hz, s, sig[i + 1] - ri + 2.0 * ri * k / steps);
                    s += (float) (arc / steps);
                }
            }
        }
        // the far end
        double ux = Math.signum(px[n - 1] - px[n - 2]), uz = Math.signum(pz[n - 1] - pz[n - 2]);
        add(px[n - 1], pz[n - 1], (float) ux, (float) uz, s, sig[n - 1]);
        length = s;
        // heights: from node to node, a smooth ramp; then the arch on top, as high as stays walkable
        for (int i = 0; i < samples.size(); i++) {
            Sample a = samples.get(i);
            samples.set(i, new Sample(a.x(), a.z(), a.dx(), a.dz(), a.s(), ramp(a.y(), sig)));
        }
        List<Sample> flat = new ArrayList<>(samples);
        float want = mode == ARCH ? Math.min(0.28F * length, 10.0F) : 0.0F;
        for (int tries = 0; tries < 8; tries++) {
            samples.clear();
            for (Sample a : flat) {
                double up = want * Math.sin(Math.PI * Mth.clamp(a.s() / Math.max(length, 0.01F), 0, 1));
                samples.add(new Sample(a.x(), a.z(), a.dx(), a.dz(), a.s(), a.y() + up));
            }
            walkable = steepest() <= MAX_SLOPE;
            if (walkable || want <= 0.01F) break;
            want *= 0.7F;
        }
        arch = want;
        if (mode == TUNNEL || mode == WALL) {
            walkable = true; // (not walked on: any slope does)
        }
        if (total < 0.5F) {
            walkable = false;
        }
    }

    /** Moves end {@code i} half a block towards {@code j} (an emitter's front edge). */
    private static void pull(double[] px, double[] pz, int i, int j) {
        px[i] += Math.signum(px[j] - px[i]) * 0.5;
        pz[i] += Math.signum(pz[j] - pz[i]) * 0.5;
    }

    private void add(double x, double z, float dx, float dz, float s, double sigma) {
        samples.add(new Sample(x, z, dx, dz, s, sigma)); // (y holds the distance along the corners' line, for now)
    }

    /** The height at {@code sigma} along the line through the nodes: eased from one node's height to the next's. */
    private double ramp(double sigma, double[] sig) {
        int n = nodes.size();
        for (int i = 0; i < n - 1; i++) {
            if (sigma <= sig[i + 1] || i == n - 2) {
                double t = Mth.clamp((sigma - sig[i]) / Math.max(sig[i + 1] - sig[i], 1e-4), 0, 1);
                double e = 0.25 * t + 0.75 * t * t * (3 - 2 * t); // (levelling out at each end, onto the emitter or bender)
                return Mth.lerp(e, nodes.get(i).y(), nodes.get(i + 1).y());
            }
        }
        return nodes.get(0).y();
    }

    private float steepest() {
        float worst = 0;
        for (int i = 1; i < samples.size(); i++) {
            Sample a = samples.get(i - 1), b = samples.get(i);
            float ds = b.s() - a.s();
            if (ds > 1e-4F) worst = Math.max(worst, (float) Math.abs(b.y() - a.y()) / ds);
        }
        return worst;
    }

    // ---- using it ----

    /** A tunnel's radius (blocks): wider for a wider bridge. */
    public float tunnelRadius() {
        return 0.7F + 0.5F * width;
    }

    /** The point along it nearest to (x, z) (flat), with its distance; null if there are none. */
    @Nullable
    public Sample nearest(double x, double y, double z, float[] dist) {
        int i = nearestIndex(x, y, z, dist);
        return i < 0 ? null : samples.get(i);
    }

    /** Which sample is nearest (x, z) of those near enough y, and how far it is (flat); -1 if none. */
    public int nearestIndex(double x, double y, double z, float[] dist) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < samples.size(); i++) {
            Sample a = samples.get(i);
            double d = (a.x() - x) * (a.x() - x) + (a.z() - z) * (a.z() - z);
            double h = y - a.y();
            if (h < -3.0 || h > 4.0) continue;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        dist[0] = (float) Math.sqrt(bd);
        return best;
    }

    /** How fast it rises (blocks up per block along) at sample {@code i}, going forwards. */
    public float riseAt(int i) {
        Sample a = samples.get(Math.max(0, i - 1)), b = samples.get(Math.min(samples.size() - 1, i + 1));
        float ds = b.s() - a.s();
        return ds < 1e-4F ? 0 : (float) (b.y() - a.y()) / ds;
    }

    /** How steep it is at sample {@code i}. */
    public float slopeAt(int i) {
        Sample a = samples.get(Math.max(0, i - 1)), b = samples.get(Math.min(samples.size() - 1, i + 1));
        float ds = b.s() - a.s();
        return ds < 1e-4F ? 0 : (float) Math.abs(b.y() - a.y()) / ds;
    }

    /**
     * Its surface under (x, z): how high its top is there (between the samples either side, smoothly), how far (x, z) is
     * from its middle line, which sample - or NaN if it isn't near (x, y, z).
     */
    public double surface(double x, double y, double z, float[] dist, Sample[] at) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < samples.size(); i++) {
            Sample a = samples.get(i);
            double h = y - a.y();
            if (h < -2.0 || h > 2.0) continue;
            double d = (a.x() - x) * (a.x() - x) + (a.z() - z) * (a.z() - z);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        if (best < 0) return Double.NaN;
        Sample a = samples.get(best);
        // (how far along from it, towards the next or the last)
        double along = (x - a.x()) * a.dx() + (z - a.z()) * a.dz();
        int j = along >= 0 ? Math.min(best + 1, samples.size() - 1) : Math.max(best - 1, 0);
        Sample b = samples.get(j);
        double span = Math.abs(b.s() - a.s());
        double t = span < 1e-4 ? 0 : Mth.clamp(Math.abs(along) / span, 0, 1);
        double side = (x - a.x()) * -a.dz() + (z - a.z()) * a.dx();
        dist[0] = (float) Math.abs(side);
        if (dist.length > 1) dist[1] = (float) side;
        at[0] = a;
        return Mth.lerp(t, a.y(), b.y()) + 2.0 / 16;
    }

    /** The lowest and highest corner of all of it (for drawing). */
    public double[] bounds() {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Sample a : samples) {
            b[0] = Math.min(b[0], a.x() - 2.5);
            b[1] = Math.min(b[1], a.y() - 0.5);
            b[2] = Math.min(b[2], a.z() - 2.5);
            b[3] = Math.max(b[3], a.x() + 2.5);
            b[4] = Math.max(b[4], a.y() + 3.5);
            b[5] = Math.max(b[5], a.z() + 2.5);
        }
        return b;
    }

    public static int[] pack(List<Node> nodes) {
        int[] out = new int[nodes.size() * 4];
        for (int i = 0; i < nodes.size(); i++) {
            Node nd = nodes.get(i);
            out[i * 4] = nd.x();
            out[i * 4 + 1] = nd.y();
            out[i * 4 + 2] = nd.z();
            out[i * 4 + 3] = Math.round(nd.radius() * 2);
        }
        return out;
    }

    public static List<Node> unpack(int[] a) {
        List<Node> out = new ArrayList<>();
        for (int i = 0; i + 3 < a.length; i += 4) {
            out.add(new Node(a[i], a[i + 1], a[i + 2], a[i + 3] / 2.0F));
        }
        return out;
    }
}
