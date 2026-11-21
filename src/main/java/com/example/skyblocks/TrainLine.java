package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A line of train rails (TrainRailBlock): each rail joined to at most two others, so a line is a simple path (or a
 * loop) - walked one way or the other from any rail on it. Where trains run (its rails' middles) and the stations along
 * it (StationBlock, beside a rail).
 */
public final class TrainLine {
    /** How far a line's walked at most (rails). */
    public static final int MOST = 4096;

    private TrainLine() {}

    public static boolean isRail(BlockGetter level, BlockPos p) {
        return level.getBlockState(p).getBlock() instanceof TrainRailBlock;
    }

    /** The rail a point's on (or just over), or null. */
    @Nullable
    public static BlockPos railAt(BlockGetter level, Vec3 p) {
        BlockPos b = BlockPos.containing(p);
        if (isRail(level, b)) {
            return b;
        }
        if (isRail(level, b.below())) {
            return b.below();
        }
        return isRail(level, b.above()) ? b.above() : null;
    }

    /** Where a train runs over a rail: its middle, a little over it (half way up a slope). */
    public static Vec3 centre(BlockGetter level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        boolean up = st.getBlock() instanceof TrainRailBlock && st.getValue(TrainRailBlock.SHAPE).isAscending();
        return new Vec3(p.getX() + 0.5, p.getY() + 0.1 + (up ? 0.5 : 0.0), p.getZ() + 0.5);
    }

    /** The ways out of a rail (its shape's two ends). */
    private static Direction[] exits(RailShape s) {
        return switch (s) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> new Direction[] {Direction.NORTH, Direction.SOUTH};
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new Direction[] {Direction.EAST, Direction.WEST};
            case SOUTH_EAST -> new Direction[] {Direction.SOUTH, Direction.EAST};
            case SOUTH_WEST -> new Direction[] {Direction.SOUTH, Direction.WEST};
            case NORTH_WEST -> new Direction[] {Direction.NORTH, Direction.WEST};
            case NORTH_EAST -> new Direction[] {Direction.NORTH, Direction.EAST};
        };
    }

    /** The rails joined to one (up to two): out of each of its ends - level, a block up, or a block down. */
    public static List<BlockPos> neighbours(BlockGetter level, BlockPos p) {
        List<BlockPos> out = new ArrayList<>(2);
        BlockState st = level.getBlockState(p);
        if (!(st.getBlock() instanceof TrainRailBlock)) {
            return out;
        }
        for (Direction d : exits(st.getValue(TrainRailBlock.SHAPE))) {
            BlockPos q = p.relative(d);
            for (BlockPos c : new BlockPos[] {q, q.above(), q.below()}) {
                if (isRail(level, c) && joins(level, c, p)) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
    }

    /** Whether rail a has an end towards rail b (at its height, or a block off). */
    private static boolean joins(BlockGetter level, BlockPos a, BlockPos b) {
        BlockState st = level.getBlockState(a);
        for (Direction d : exits(st.getValue(TrainRailBlock.SHAPE))) {
            BlockPos q = a.relative(d);
            if (q.getX() == b.getX() && q.getZ() == b.getZ() && Math.abs(q.getY() - b.getY()) <= 1) {
                return true;
            }
        }
        return false;
    }

    /** The rails from start on along the line, away from 'from' (start not in it): up to MOST, stopping at its end or back at start. */
    public static List<BlockPos> walk(BlockGetter level, BlockPos start, @Nullable BlockPos from, int most) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos prev = from, at = start;
        for (int i = 0; i < Math.min(most, MOST); i++) {
            BlockPos next = null;
            for (BlockPos n : neighbours(level, at)) {
                if (!n.equals(prev)) {
                    next = n;
                    break;
                }
            }
            if (next == null || next.equals(start)) {
                break;
            }
            out.add(next);
            prev = at;
            at = next;
        }
        return out;
    }

    /** The two ways along a line from a rail (each away from the other end). */
    public static List<List<BlockPos>> bothWays(BlockGetter level, BlockPos start, int most) {
        List<BlockPos> n = neighbours(level, start);
        List<List<BlockPos>> out = new ArrayList<>();
        for (BlockPos first : n) {
            List<BlockPos> way = new ArrayList<>();
            way.add(first);
            way.addAll(walk(level, first, start, most - 1));
            out.add(way);
        }
        return out;
    }

    /** A station beside a rail (a block across or diagonal, at its height or a block up or down), or null. */
    @Nullable
    public static BlockPos stationBy(BlockGetter level, BlockPos rail) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos p = rail.offset(dx, dy, dz);
                    if ((dx != 0 || dz != 0) && level.getBlockState(p).getBlock() instanceof StationBlock) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    /** A station's rail: the nearest train rail beside it, or null. */
    @Nullable
    public static BlockPos railBy(BlockGetter level, BlockPos station) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos p = station.offset(dx, dy, dz);
                    if (isRail(level, p) && p.distSqr(station) < bestD) {
                        bestD = p.distSqr(station);
                        best = p;
                    }
                }
            }
        }
        return best;
    }
}
