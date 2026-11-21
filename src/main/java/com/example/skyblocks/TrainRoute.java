package com.example.skyblocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a train can go: along a line of train rails (TrainLine), or along smooth track (TrackNetwork) - through its
 * nodes, the way each switch is set. Its stations (StationBlockEntity) found along the way.
 */
public final class TrainRoute {
    private TrainRoute() {}

    /** The way on: its points (from just past 'at'), whether it went as far as asked (more beyond), whether it came round to its start. */
    public record Onward(List<Vec3> pts, boolean open, boolean looped) {}

    /** How near a point must be to track to be on it. */
    private static final double ON = 0.8;

    public static boolean onLine(Level level, Vec3 p) {
        return TrainLine.railAt(level, p) != null || TrackNetwork.of(level).nearest(p, ON) != null;
    }

    /** From a point on a line, going this way: the points on along it, up to 'most' blocks. */
    public static Onward onward(Level level, Vec3 at, Vec3 dir, float most) {
        TrackNetwork net = TrackNetwork.of(level);
        TrackNetwork.Near near = net.nearest(at, ON);
        BlockPos rail = TrainLine.railAt(level, at);
        if (near != null && (rail == null || near.dist() < 0.45)) {
            return track(net, near, at, dir, most);
        }
        if (rail != null) {
            return rails(level, rail, at, dir, most);
        }
        return new Onward(List.of(), false, false);
    }

    private static Onward rails(Level level, BlockPos rail, Vec3 at, Vec3 dir, float most) {
        List<Vec3> out = new ArrayList<>();
        Vec3 c = TrainLine.centre(level, rail);
        Vec3 h = new Vec3(dir.x, 0, dir.z);
        if (c.subtract(at).dot(h) > 0.05) {
            out.add(c);
        }
        List<BlockPos> way = null;
        for (List<BlockPos> w : TrainLine.bothWays(level, rail, (int) most + 2)) {
            if (Vec3.atLowerCornerOf(w.get(0).subtract(rail)).dot(h) > 0.1) {
                way = w;
            }
        }
        boolean looped = false;
        if (way != null) {
            for (BlockPos r : way) {
                out.add(TrainLine.centre(level, r));
            }
            BlockPos last = way.get(way.size() - 1);
            looped = way.size() > 2 && TrainLine.neighbours(level, last).contains(rail);
        }
        return new Onward(out, way != null && way.size() >= (int) most, looped);
    }

    private static Onward track(TrackNetwork net, TrackNetwork.Near near, Vec3 at, Vec3 dir, float most) {
        List<Vec3> out = new ArrayList<>();
        TrackNetwork.Seg seg = near.seg();
        int i = near.index();
        boolean fwd = seg.tan[i].dot(dir) >= 0;
        int startSeg = seg.id;
        boolean startFwd = fwd;
        float went = 0.0F;
        Vec3 last = at;
        boolean first = true;
        for (int guard = 0; guard < 10000; guard++) {
            // along this piece, to its end
            int n = seg.pts.length;
            int from = first ? i : (fwd ? 0 : n - 1);
            for (int j = from; fwd ? j < n : j >= 0; j += fwd ? 1 : -1) {
                Vec3 p = seg.pts[j];
                if (first && p.subtract(at).dot(dir) <= 0.02) {
                    continue;
                }
                went += (float) p.distanceTo(last);
                last = p;
                out.add(p);
                if (went >= most) {
                    return new Onward(out, true, false);
                }
            }
            first = false;
            // through its node, on to the next piece (the way its switch is set)
            int end = fwd ? 1 : 0;
            TrackNetwork.Node node = net.nodes.get(seg.node(end));
            if (node == null) {
                break;
            }
            int arrivedSide = seg.sideAt(end);
            int next = node.taken(-arrivedSide);
            TrackNetwork.Seg ns = next < 0 ? null : net.segs.get(next);
            if (ns == null) {
                break;
            }
            fwd = ns.a == node.id && ns.aSide == -arrivedSide;
            if (ns.id == startSeg && fwd == startFwd) {
                return new Onward(out, false, true);
            }
            seg = ns;
        }
        return new Onward(out, false, false);
    }

    // ---- stations ----

    public static final Set<StationBlockEntity> CLIENT = Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public static final Set<StationBlockEntity> SERVER = Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private static List<StationBlockEntity> stations(Level level) {
        List<StationBlockEntity> out = new ArrayList<>();
        synchronized (TrainRoute.class) {
            for (StationBlockEntity be : level.isClientSide ? CLIENT : SERVER) {
                if (be.getLevel() == level && !be.isRemoved()) {
                    out.add(be);
                }
            }
        }
        return out;
    }

    /** Whether a station's by a point on a line (within 3.5 blocks across, 2.5 up or down). */
    private static boolean by(BlockPos st, Vec3 p) {
        double dx = st.getX() + 0.5 - p.x, dz = st.getZ() + 0.5 - p.z, dy = st.getY() + 0.5 - p.y;
        return dx * dx + dz * dz <= 3.5 * 3.5 && Math.abs(dy) <= 2.5;
    }

    /** The stations by a way, in order along it: each with the index of the point it's nearest. */
    public record Stop(BlockPos station, int index) {}

    public static List<Stop> stationsAlong(Level level, List<Vec3> pts) {
        List<Stop> out = new ArrayList<>();
        for (StationBlockEntity be : stations(level)) {
            BlockPos st = be.getBlockPos();
            int best = -1;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < pts.size(); i++) {
                Vec3 p = pts.get(i);
                if (by(st, p)) {
                    double d = p.distanceToSqr(Vec3.atCenterOf(st));
                    if (d < bd) {
                        bd = d;
                        best = i;
                    }
                }
            }
            if (best >= 0) {
                out.add(new Stop(st, best));
            }
        }
        out.sort((a, b) -> Integer.compare(a.index, b.index));
        return out;
    }

    /** Where a train stops for a station: the point on a line nearest it (a rail's middle, or along track), or null. */
    @Nullable
    public static Vec3 stopPoint(Level level, BlockPos station) {
        Vec3 best = null;
        double bd = Double.MAX_VALUE;
        BlockPos rail = TrainLine.railBy(level, station);
        if (rail != null) {
            best = TrainLine.centre(level, rail);
            bd = best.distanceToSqr(Vec3.atCenterOf(station));
        }
        TrackNetwork.Near n = TrackNetwork.of(level).nearest(Vec3.atCenterOf(station), 4.0);
        if (n != null) {
            Vec3 p = n.seg().pts[n.index()];
            if (by(station, p) && p.distanceToSqr(Vec3.atCenterOf(station)) < bd) {
                best = p;
            }
        }
        return best;
    }
}
