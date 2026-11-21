package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Signalling: where trains are and where they've claimed (TrainEntity.bodyPts, claimPts) - track another train mustn't
 * go on. A train sets off only on track that's clear (or finds a way round), stops short of a train ahead, and a switch
 * a train's on or has claimed can't be flipped.
 */
public final class TrainSignals {
    /** How near (blocks) a point is to be on another train's track: across it, up and down. */
    public static final double NEAR = 2.4, NEAR_Y = 3.0;

    private TrainSignals() {}

    /** Points, by 4-block cell, for finding the ones near a place quickly. */
    public static final class Occupancy {
        private final Map<Long, List<Vec3>> cells = new HashMap<>();
        private boolean any;

        private static long key(int x, int y, int z) {
            return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
        }

        void add(List<Vec3> pts) {
            for (Vec3 p : pts) {
                cells.computeIfAbsent(key((int) Math.floor(p.x / 4), (int) Math.floor(p.y / 4), (int) Math.floor(p.z / 4)), k -> new ArrayList<>()).add(p);
                any = true;
            }
        }

        public boolean near(Vec3 p) {
            if (!any) {
                return false;
            }
            int cx = (int) Math.floor(p.x / 4), cy = (int) Math.floor(p.y / 4), cz = (int) Math.floor(p.z / 4);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        List<Vec3> c = cells.get(key(cx + dx, cy + dy, cz + dz));
                        if (c == null) {
                            continue;
                        }
                        for (Vec3 q : c) {
                            double hx = q.x - p.x, hz = q.z - p.z;
                            if (hx * hx + hz * hz < NEAR * NEAR && Math.abs(q.y - p.y) < NEAR_Y) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        /** The first of a way's points that's near, or -1. */
        public int first(List<Vec3> way) {
            for (int i = 0; i < way.size(); i++) {
                if (near(way.get(i))) {
                    return i;
                }
            }
            return -1;
        }
    }

    /** Where every train but one is (and, with claims, where they're going). */
    public static Occupancy others(Level level, @Nullable TrainEntity except, boolean claims) {
        Occupancy occ = new Occupancy();
        List<TrainEntity> trains;
        synchronized (TrainEntity.class) {
            trains = new ArrayList<>(level.isClientSide ? TrainEntity.CLIENT : TrainEntity.SERVER);
        }
        for (TrainEntity t : trains) {
            if (t == except || t.isRemoved() || t.level() != level) {
                continue;
            }
            occ.add(t.bodyPts());
            if (claims) {
                occ.add(t.claimPts());
            }
        }
        return occ;
    }

    /** Whether a train's on, or has claimed, the track at a place (a switch: then it's not to be flipped). */
    public static boolean claimed(Level level, Vec3 at) {
        return others(level, null, true).near(at);
    }
}
