package com.example.skyblocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    // ---- every way on, through switches either way ----

    /** A way on through the track: its points, how far each is from the start, and the switch settings it takes. */
    public record Branch(List<Vec3> pts, List<Float> dist, List<int[]> switches) {}

    private record State(int seg, boolean fwd, int from, float d0, Vec3 last, @Nullable State parent, int[] sw) {}

    /**
     * Every way on from a point on smooth track, going this way (any way each switch could send it), up to 'most'
     * blocks: one Branch for each way's end (its points from the start). Empty on block rails (they don't branch).
     */
    public static List<Branch> branches(Level level, Vec3 at, Vec3 dir, float most) {
        List<Branch> out = new ArrayList<>();
        TrackNetwork net = TrackNetwork.of(level);
        TrackNetwork.Near near = net.nearest(at, ON);
        BlockPos rail = TrainLine.railAt(level, at);
        if (near == null || (rail != null && near.dist() >= 0.45)) {
            return out;
        }
        TrackNetwork.Seg seg0 = near.seg();
        java.util.ArrayDeque<State> todo = new java.util.ArrayDeque<>();
        todo.add(new State(seg0.id, seg0.tan[near.index()].dot(dir) >= 0, near.index(), 0.0F, at, null, null));
        java.util.Set<Long> seen = new java.util.HashSet<>();
        int guard = 0;
        while (!todo.isEmpty() && guard++ < 512) {
            State st = todo.poll();
            TrackNetwork.Seg seg = net.segs.get(st.seg);
            if (seg == null || !seen.add(st.seg * 2L + (st.fwd ? 1 : 0))) {
                continue;
            }
            // along this piece
            float d = st.d0;
            Vec3 last = st.last;
            int n = seg.pts.length;
            boolean first = st.parent == null;
            int from = first ? st.from : (st.fwd ? 0 : n - 1);
            for (int j = from; st.fwd ? j < n : j >= 0; j += st.fwd ? 1 : -1) {
                Vec3 p = seg.pts[j];
                if (first && p.subtract(at).dot(dir) <= 0.02) {
                    continue;
                }
                d += (float) p.distanceTo(last);
                last = p;
            }
            // on through its node, every way
            List<int[]> nexts = new ArrayList<>();
            if (d < most) {
                int end = st.fwd ? 1 : 0;
                TrackNetwork.Node node = net.nodes.get(seg.node(end));
                if (node != null) {
                    int arrived = seg.sideAt(end);
                    List<Integer> side = node.side(-arrived);
                    for (int k = 0; k < side.size(); k++) {
                        TrackNetwork.Seg ns = net.segs.get(side.get(k));
                        if (ns == null) {
                            continue;
                        }
                        boolean fwd = ns.a == node.id && ns.aSide == -arrived;
                        int[] sw = side.size() > 1 ? new int[] {node.id, -arrived > 0 ? 1 : 0, k} : null;
                        nexts.add(new int[] {ns.id, fwd ? 1 : 0, sw == null ? -1 : sw[0], sw == null ? -1 : sw[1], sw == null ? -1 : sw[2]});
                    }
                }
            }
            State done = new State(st.seg, st.fwd, st.from, st.d0, st.last, st.parent, st.sw);
            if (nexts.isEmpty()) {
                out.add(collect(net, done, at, dir));
            }
            for (int[] nx : nexts) {
                todo.add(new State(nx[0], nx[1] == 1, 0, d, last, done, nx[2] < 0 ? null : new int[] {nx[2], nx[3], nx[4]}));
            }
        }
        return out;
    }

    /** A way's points (and distances, and switches), from its start to the end of the piece a state's on. */
    private static Branch collect(TrackNetwork net, State end, Vec3 at, Vec3 dir) {
        List<State> chain = new ArrayList<>();
        for (State s = end; s != null; s = s.parent) {
            chain.add(0, s);
        }
        List<Vec3> pts = new ArrayList<>();
        List<Float> dist = new ArrayList<>();
        List<int[]> sw = new ArrayList<>();
        float d = 0.0F;
        Vec3 last = at;
        for (int c = 0; c < chain.size(); c++) {
            State st = chain.get(c);
            if (st.sw != null) {
                sw.add(st.sw);
            }
            TrackNetwork.Seg seg = net.segs.get(st.seg);
            int n = seg.pts.length;
            int from = c == 0 ? st.from : (st.fwd ? 0 : n - 1);
            for (int j = from; st.fwd ? j < n : j >= 0; j += st.fwd ? 1 : -1) {
                Vec3 p = seg.pts[j];
                if (c == 0 && p.subtract(at).dot(dir) <= 0.02) {
                    continue;
                }
                d += (float) p.distanceTo(last);
                last = p;
                pts.add(p);
                dist.add(d);
            }
        }
        return new Branch(pts, dist, sw);
    }

    /**
     * The way to a spot on the track, any way the switches could go: the switches to set for it (node, side, piece), and
     * how far it is; or null if it can't be reached within 'most' blocks.
     */
    @Nullable
    public static Branch routeTo(Level level, Vec3 at, Vec3 dir, Vec3 target, float most) {
        Branch best = null;
        float bd = Float.MAX_VALUE;
        for (Branch b : branches(level, at, dir, most)) {
            for (int i = 0; i < b.pts().size(); i++) {
                if (b.pts().get(i).distanceToSqr(target) < 1.0 && b.dist().get(i) < bd) {
                    bd = b.dist().get(i);
                    best = b;
                }
            }
        }
        return best;
    }

    /** Sets switches (node, side, piece), and tells the players. */
    public static void setSwitches(Level level, List<int[]> switches) {
        if (switches.isEmpty() || !(level instanceof net.minecraft.server.level.ServerLevel sl)) {
            return;
        }
        TrackNetwork net = TrackNetwork.of(level);
        boolean changed = false;
        for (int[] s : switches) {
            TrackNetwork.Node n = net.nodes.get(s[0]);
            if (n != null && n.pick[s[1]] != s[2]) {
                n.pick[s[1]] = s[2];
                changed = true;
            }
        }
        if (changed) {
            net.changed();
            TrackPayloads.send(sl);
            TrackNetwork.Node first = net.nodes.get(switches.get(0)[0]);
            if (first != null) {
                level.playSound(null, BlockPos.containing(first.pos), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.4F, 1.5F);
            }
        }
    }

    /** Every station reachable on from a point (any way the switches could go; on rails, along the line), nearest first. */
    public static List<BlockPos> stationsAnyWay(Level level, Vec3 at, Vec3 dir, float most) {
        List<Branch> bs = branches(level, at, dir, most);
        java.util.Map<BlockPos, Float> near = new java.util.HashMap<>();
        if (bs.isEmpty()) {
            List<Vec3> pts = onward(level, at, dir, most).pts();
            List<BlockPos> out = new ArrayList<>();
            for (Stop s : stationsAlong(level, pts)) {
                out.add(s.station());
            }
            return out;
        }
        for (Branch b : bs) {
            for (Stop s : stationsAlong(level, b.pts())) {
                float d = b.dist().get(s.index());
                near.merge(s.station(), d, Math::min);
            }
        }
        List<BlockPos> out = new ArrayList<>(near.keySet());
        out.sort((a, b) -> Float.compare(near.get(a), near.get(b)));
        return out;
    }

    // ---- planning a way there: through switches, backing up past one if it must ----

    /**
     * One leg of a planned way: its points (the way its front goes), the switches to set before it, whether it starts
     * by turning back (its back now its front), and if so whether it turns back where its front stopped (its points
     * then start at its old front: the length of it to be skipped).
     */
    public record Leg(List<Vec3> pts, List<int[]> switches, boolean reverse, boolean fromHead) {}

    private record PState(int seg, boolean fwd, int entry, float cost, @Nullable PState parent, @Nullable int[] sw, boolean reversed, boolean fromHead,
            boolean initial, float since) {}

    /**
     * The best way along smooth track from a train (its front and back, the ways they face out) to a spot, each switch
     * set as it needs - or backing up past a switch onto the other way, where its whole length fits. Null if none (or
     * it's not on smooth track).
     */
    @Nullable
    public static List<Leg> plan(Level level, Vec3 headAt, Vec3 headDir, Vec3 tailAt, Vec3 tailDir, float length, Vec3 target, boolean mayReverse) {
        return plan(level, headAt, headDir, tailAt, tailDir, length, target, mayReverse, null);
    }

    /** As above, but never through track another train's on or has claimed ('blocked'): round it, if there's a way. */
    @Nullable
    public static List<Leg> plan(Level level, Vec3 headAt, Vec3 headDir, Vec3 tailAt, Vec3 tailDir, float length, Vec3 target, boolean mayReverse,
            @Nullable java.util.function.Predicate<Vec3> blocked) {
        TrackNetwork net = TrackNetwork.of(level);
        TrackNetwork.Near hn = net.nearest(headAt, ON), tn = net.nearest(tailAt, ON);
        if (hn == null) {
            return null;
        }
        java.util.PriorityQueue<PState> todo = new java.util.PriorityQueue<>((a, b) -> Float.compare(a.cost, b.cost));
        todo.add(new PState(hn.seg().id, hn.seg().tan[hn.index()].dot(headDir) >= 0, hn.index(), 0.0F, null, null, false, false, true, 0.0F));
        if (mayReverse && tn != null) {
            todo.add(new PState(tn.seg().id, tn.seg().tan[tn.index()].dot(tailDir) >= 0, tn.index(), 4.0F, null, null, true, false, true, 0.0F));
        }
        java.util.Set<Long> seen = new java.util.HashSet<>();
        float reverseRoom = length + 2.0F;
        int guard = 0;
        while (!todo.isEmpty() && guard++ < 4000) {
            PState st = todo.poll();
            TrackNetwork.Seg seg = net.segs.get(st.seg);
            if (seg == null) {
                continue;
            }
            long key = st.seg * 8L + (st.fwd ? 4 : 0) + (st.reversed ? 2 : 0) + (st.initial ? 1 : 0);
            if (!seen.add(key)) {
                continue;
            }
            int n = seg.pts.length, step = st.fwd ? 1 : -1;
            // the spot on this piece, ahead of where it came on: there
            float d = 0.0F;
            int best = -1;
            double bd = 1.0;
            int stopAt = -1;
            for (int j = st.entry; j >= 0 && j < n; j += step) {
                if (j != st.entry) {
                    d += (float) seg.pts[j].distanceTo(seg.pts[j - step]);
                }
                if (blocked != null && j != st.entry && blocked.test(seg.pts[j])) {
                    stopAt = j; // (another train: no further this way)
                    break;
                }
                double dd = seg.pts[j].distanceToSqr(target);
                if (dd < bd && (j != st.entry || !st.initial)) {
                    bd = dd;
                    best = j; // (the point nearest it: its front stops right by it)
                }
            }
            if (best >= 0) {
                return legs(net, st, best);
            }
            if (stopAt >= 0) {
                continue;
            }
            // or back up, once its whole length is past the last switch it went through (or, setting out, past itself)
            boolean reversedHere = false;
            float since = st.since;
            if (mayReverse) {
                for (int j = st.entry + step; j >= 0 && j < n; j += step) {
                    since += (float) seg.pts[j].distanceTo(seg.pts[j - step]);
                    if (since >= reverseRoom) {
                        todo.add(new PState(st.seg, !st.fwd, j, st.cost + reverseRoom * 2.0F + 30.0F, st, null, true, true, false, 0.0F));
                        reversedHere = true;
                        break;
                    }
                }
            } else {
                since += d;
            }
            // on through its node: every piece on its other side
            int end = st.fwd ? 1 : 0;
            TrackNetwork.Node node = net.nodes.get(seg.node(end));
            if (node != null) {
                int arrived = seg.sideAt(end);
                List<Integer> side = node.side(-arrived);
                // (a switch passed: its whole length must clear it again before it may back up)
                float carry = node.isSwitch() ? 0.0F : (reversedHere ? reverseRoom : since);
                for (int k = 0; k < side.size(); k++) {
                    TrackNetwork.Seg ns = net.segs.get(side.get(k));
                    if (ns == null) {
                        continue;
                    }
                    boolean fwd = ns.a == node.id && ns.aSide == -arrived;
                    int[] sw = side.size() > 1 ? new int[] {node.id, -arrived > 0 ? 1 : 0, k} : null;
                    todo.add(new PState(ns.id, fwd, fwd ? 0 : ns.pts.length - 1, st.cost + d, st, sw, false, false, false, carry));
                }
            }
        }
        return null;
    }

    /** The legs of a found way: split where it turns back. */
    private static List<Leg> legs(TrackNetwork net, PState goal, int goalIndex) {
        List<PState> chain = new ArrayList<>();
        for (PState s = goal; s != null; s = s.parent) {
            chain.add(0, s);
        }
        List<Leg> out = new ArrayList<>();
        List<Vec3> pts = new ArrayList<>();
        List<int[]> sw = new ArrayList<>();
        boolean rev = chain.get(0).reversed, fromHead = false;
        for (int c = 0; c < chain.size(); c++) {
            PState st = chain.get(c);
            if (c > 0 && st.reversed) {
                out.add(new Leg(pts, sw, rev, fromHead));
                pts = new ArrayList<>();
                sw = new ArrayList<>();
                rev = true;
                fromHead = st.fromHead;
            }
            if (st.sw != null) {
                sw.add(st.sw);
            }
            TrackNetwork.Seg seg = net.segs.get(st.seg);
            int step = st.fwd ? 1 : -1;
            int stop;
            if (c == chain.size() - 1) {
                stop = goalIndex;
            } else if (chain.get(c + 1).reversed) {
                stop = chain.get(c + 1).entry;
            } else {
                stop = st.fwd ? seg.pts.length - 1 : 0;
            }
            for (int j = st.entry; ; j += step) {
                pts.add(seg.pts[j]);
                if (j == stop || j + step < 0 || j + step >= seg.pts.length) {
                    break;
                }
            }
        }
        out.add(new Leg(pts, sw, rev, fromHead));
        return out;
    }

    /** Every line a point's on joins (smooth track through all its nodes, or a line of rails): its points, for a map. */
    public static List<List<Vec3>> network(Level level, Vec3 at) {
        List<List<Vec3>> out = new ArrayList<>();
        TrackNetwork net = TrackNetwork.of(level);
        TrackNetwork.Near near = net.nearest(at, ON);
        if (near == null) {
            BlockPos rail = TrainLine.railAt(level, at);
            if (rail != null) {
                List<Vec3> line = new ArrayList<>();
                line.add(TrainLine.centre(level, rail));
                for (List<BlockPos> w : TrainLine.bothWays(level, rail, TrainLine.MOST)) {
                    for (BlockPos r : w) {
                        line.add(TrainLine.centre(level, r));
                    }
                }
                out.add(line);
            }
            return out;
        }
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Integer> todo = new java.util.ArrayDeque<>();
        todo.add(near.seg().id);
        while (!todo.isEmpty() && seen.size() < 2048) {
            int id = todo.poll();
            TrackNetwork.Seg s = net.segs.get(id);
            if (s == null || !seen.add(id)) {
                continue;
            }
            out.add(java.util.Arrays.asList(s.pts));
            for (int end = 0; end < 2; end++) {
                TrackNetwork.Node n = net.nodes.get(s.node(end));
                if (n != null) {
                    todo.addAll(n.sides.get(0));
                    todo.addAll(n.sides.get(1));
                }
            }
        }
        return out;
    }

    /** Every station by a train's network (the lines its point joins). */
    public static List<BlockPos> stationsOn(Level level, Vec3 at) {
        List<BlockPos> out = new ArrayList<>();
        List<List<Vec3>> lines = network(level, at);
        for (StationBlockEntity be : stations(level)) {
            BlockPos st = be.getBlockPos();
            search:
            for (List<Vec3> line : lines) {
                for (Vec3 p : line) {
                    if (by(st, p)) {
                        out.add(st);
                        break search;
                    }
                }
            }
        }
        return out;
    }

    // ---- stations ----

    public static final Set<StationBlockEntity> CLIENT = Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public static final Set<StationBlockEntity> SERVER = Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** Every station loaded in a world. */
    public static List<BlockPos> allStations(Level level) {
        List<BlockPos> out = new ArrayList<>();
        for (StationBlockEntity be : stations(level)) {
            out.add(be.getBlockPos());
        }
        return out;
    }

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
