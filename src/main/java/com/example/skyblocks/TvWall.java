package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A wall of televisions: columns of them side by side, which may turn corners - inside ones (round a room: all the
 * way round, a ring) or outside ones (round a pillar) - all as tall as each other: the most screen there is from its
 * first (bottom left, as you look at it) column. One that stands out of it is a television of its own.
 */
public record TvWall(BlockPos anchor, List<Column> columns, int height, boolean ring) {
    /** A column: its bottom television, and the way it faces. */
    public record Column(BlockPos bottom, Direction facing) {}

    public static final int MAX_COLUMNS = 64, MAX_H = 12;

    public int width() {
        return columns.size();
    }

    /** Whether it turns anywhere. */
    public boolean curved() {
        for (Column c : columns) {
            if (c.facing() != columns.get(0).facing()) return true;
        }
        return false;
    }

    public boolean contains(BlockPos p) {
        if (p.getY() < anchor.getY() || p.getY() >= anchor.getY() + height) return false;
        for (Column c : columns) {
            if (c.bottom().getX() == p.getX() && c.bottom().getZ() == p.getZ()) return true;
        }
        return false;
    }

    /** Its middle (where its sound comes from), a little in front of it. */
    public Vec3 centre() {
        double x = 0, z = 0;
        for (Column c : columns) {
            x += c.bottom().getX() + 0.5 + c.facing().getStepX() * 0.3;
            z += c.bottom().getZ() + 0.5 + c.facing().getStepZ() * 0.3;
        }
        return new Vec3(x / columns.size(), anchor.getY() + height / 2.0, z / columns.size());
    }

    // ---- finding them

    private static boolean tv(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState st = level.getBlockState(pos);
        return st.getBlock() instanceof TelevisionBlock && st.getValue(TelevisionBlock.FACING) == facing;
    }

    /** The column after this one going right: straight on, round an inside corner (either way it's built), or an outside one. */
    @Nullable
    public static Column next(BlockGetter level, BlockPos p, Direction f) {
        Direction r = f.getCounterClockWise();
        if (tv(level, p.relative(r), f)) return new Column(p.relative(r), f);
        if (tv(level, p.relative(r).relative(f), r.getOpposite())) return new Column(p.relative(r).relative(f), r.getOpposite());
        if (tv(level, p.relative(r), r.getOpposite())) return new Column(p.relative(r), r.getOpposite());
        if (tv(level, p.relative(r).relative(f.getOpposite()), r)) return new Column(p.relative(r).relative(f.getOpposite()), r);
        return null;
    }

    /** The column before this one (whichever's next is this one). */
    @Nullable
    public static Column prev(BlockGetter level, BlockPos p, Direction f) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos q = p.offset(dx, 0, dz);
                BlockState st = level.getBlockState(q);
                if (st.getBlock() instanceof TelevisionBlock) {
                    Direction g = st.getValue(TelevisionBlock.FACING);
                    Column n = next(level, q, g);
                    if (n != null && n.bottom().equals(p) && n.facing() == f) return new Column(q, g);
                }
            }
        }
        return null;
    }

    /** The wall from this (its first column's bottom). */
    public static TvWall of(BlockGetter level, BlockPos anchor) {
        Map<BlockPos, TvWall> cache = cache(level);
        if (cache != null) {
            TvWall w = cache.get(anchor);
            if (w != null) return w;
        }
        BlockState st = level.getBlockState(anchor);
        Direction f = st.getBlock() instanceof TelevisionBlock ? st.getValue(TelevisionBlock.FACING) : Direction.NORTH;
        List<Column> chain = new ArrayList<>();
        chain.add(new Column(anchor, f));
        boolean ring = false;
        Column c = chain.get(0);
        while (chain.size() < MAX_COLUMNS) {
            Column n = next(level, c.bottom(), c.facing());
            if (n == null) break;
            if (n.bottom().equals(anchor)) {
                ring = true;
                break;
            }
            boolean seen = false;
            for (Column o : chain) if (o.bottom().equals(n.bottom())) seen = true;
            if (seen) break;
            chain.add(n);
            c = n;
        }
        // the most screen: as many columns as keeps the most area, all as tall as the shortest of them
        int bestN = 1, bestH = 1, minH = MAX_H;
        for (int i = 0; i < chain.size(); i++) {
            Column col = chain.get(i);
            int h = 1;
            while (h < minH && tv(level, col.bottom().above(h), col.facing())) h++;
            minH = Math.min(minH, h);
            if (minH * (i + 1) >= bestN * bestH) {
                bestN = i + 1;
                bestH = minH;
            }
        }
        TvWall w = new TvWall(anchor, List.copyOf(chain.subList(0, bestN)), bestH, ring && bestN == chain.size());
        if (cache != null) cache.put(anchor, w);
        return w;
    }

    /** The first column's bottom of the wall this one is in. */
    public static BlockPos anchor(BlockGetter level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof TelevisionBlock)) return pos;
        Map<BlockPos, BlockPos> cache = anchorCache(level);
        if (cache != null) {
            BlockPos a = cache.get(pos);
            if (a != null) return a;
        }
        BlockPos a = findAnchor(level, pos, st.getValue(TelevisionBlock.FACING));
        if (cache != null) cache.put(pos.immutable(), a);
        return a;
    }

    private static BlockPos findAnchor(BlockGetter level, BlockPos pos, Direction facing) {
        // (the furthest down and back it reaches - round a ring, the same column whichever it starts from)
        BlockPos p = pos;
        Direction f = facing;
        Set<BlockPos> seen = new HashSet<>();
        for (int i = 0; i < MAX_COLUMNS + MAX_H; i++) {
            if (tv(level, p.below(), f) && p.getY() > pos.getY() - MAX_H) {
                p = p.below();
                continue;
            }
            Column back = prev(level, p, f);
            if (back == null) break;
            if (!seen.add(back.bottom())) {
                p = ringStart(seen, p);
                f = level.getBlockState(p).getValue(TelevisionBlock.FACING);
                break;
            }
            p = back.bottom();
            f = back.facing();
        }
        if (of(level, p).contains(pos)) return p;
        // (not in that one: the first back along its own row whose wall it's in)
        List<BlockPos> row = new ArrayList<>();
        BlockPos q = pos;
        Direction g = facing;
        for (int i = 0; i < MAX_COLUMNS; i++) {
            Column back = prev(level, q, g);
            if (back == null || back.bottom().equals(pos) || row.contains(back.bottom())) break;
            row.add(back.bottom());
            q = back.bottom();
            g = back.facing();
        }
        for (int i = row.size() - 1; i >= 0; i--) {
            if (of(level, row.get(i)).contains(pos)) return row.get(i);
        }
        return pos;
    }

    /** Round a ring: one column that's always its start (the lowest x, then z, at this height). */
    private static BlockPos ringStart(Set<BlockPos> ring, BlockPos fallback) {
        BlockPos best = fallback;
        for (BlockPos b : ring) {
            if (b.getX() < best.getX() || b.getX() == best.getX() && b.getZ() < best.getZ()) best = b;
        }
        return best;
    }

    // ---- remembered for a tick on the client (every one of a wall's blocks asks, every frame)

    private static Level cacheLevel;
    private static long cacheTime = Long.MIN_VALUE;
    private static final Map<BlockPos, TvWall> WALLS = new HashMap<>();
    private static final Map<BlockPos, BlockPos> ANCHORS = new HashMap<>();

    @Nullable
    private static Map<BlockPos, TvWall> cache(BlockGetter level) {
        return fresh(level) ? WALLS : null;
    }

    @Nullable
    private static Map<BlockPos, BlockPos> anchorCache(BlockGetter level) {
        return fresh(level) ? ANCHORS : null;
    }

    private static boolean fresh(BlockGetter level) {
        if (!(level instanceof Level l) || !l.isClientSide) return false;
        if (l != cacheLevel || l.getGameTime() != cacheTime) {
            cacheLevel = l;
            cacheTime = l.getGameTime();
            WALLS.clear();
            ANCHORS.clear();
        }
        return true;
    }
}
