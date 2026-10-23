package com.example.skyblocks.client;

import com.example.skyblocks.InfinityCubeBlock;
import com.example.skyblocks.InfinityMirrorBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A group of infinity mirrors and cubes ready to draw: the mirrors joined into frames (side by side on one wall, same
 * facing and colour), the cubes joined into boxes (same colour, filling a whole box), with the faces that show. Made
 * for the world near the camera and for each Sky Island (in its own coordinates).
 */
public final class InfinitySet {
    /** A frame: its blocks (front faces showing), the rectangle around them on the wall, its colour. */
    record Frame(Direction facing, int plane, int minU, int minV, int maxU, int maxV, int color, List<BlockPos> blocks) {}

    /** A cube (one block, or a whole box of them): corners (inclusive), colour, outer faces that show. */
    record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int color, List<Face> faces) {}

    record Face(BlockPos pos, Direction side) {}

    static final InfinitySet EMPTY = new InfinitySet();

    final List<Frame> frames = new ArrayList<>();
    final List<Box> boxes = new ArrayList<>();

    public boolean isEmpty() {
        return frames.isEmpty() && boxes.isEmpty();
    }

    static boolean ours(BlockState state) {
        return state.getBlock() instanceof InfinityMirrorBlock || state.getBlock() instanceof InfinityCubeBlock;
    }

    private record Wall(Direction facing, int plane, int color) {}

    /** From the infinity blocks found (position -> state) in {@code level} (which tells what covers them). */
    static InfinitySet build(BlockGetter level, Map<BlockPos, BlockState> found) {
        InfinitySet set = new InfinitySet();
        Map<Wall, Set<BlockPos>> walls = new HashMap<>();
        Map<BlockPos, Integer> cubes = new HashMap<>();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (Map.Entry<BlockPos, BlockState> e : found.entrySet()) {
            BlockPos pos = e.getKey();
            BlockState state = e.getValue();
            if (state.getBlock() instanceof InfinityCubeBlock) {
                cubes.put(pos, state.getValue(InfinityCubeBlock.COLOR));
                continue;
            }
            if (!(state.getBlock() instanceof InfinityMirrorBlock)) {
                continue;
            }
            Direction facing = state.getValue(InfinityMirrorBlock.FACING);
            neighbour.setWithOffset(pos, facing);
            if (level.getBlockState(neighbour).isSolidRender(level, neighbour)) {
                continue; // its front is covered
            }
            int plane = pos.get(facing.getAxis()) + (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
            walls.computeIfAbsent(new Wall(facing, plane, state.getValue(InfinityMirrorBlock.COLOR)), w -> new HashSet<>()).add(pos);
        }
        // joined mirrors (side by side on the wall) make one frame
        for (Map.Entry<Wall, Set<BlockPos>> e : walls.entrySet()) {
            Wall wall = e.getKey();
            Set<BlockPos> left = new HashSet<>(e.getValue());
            while (!left.isEmpty()) {
                BlockPos start = left.iterator().next();
                left.remove(start);
                List<BlockPos> blocks = new ArrayList<>();
                ArrayDeque<BlockPos> queue = new ArrayDeque<>();
                queue.add(start);
                int minU = Integer.MAX_VALUE, minV = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE, maxV = Integer.MIN_VALUE;
                while (!queue.isEmpty()) {
                    BlockPos p = queue.poll();
                    blocks.add(p);
                    int u = u(p, wall.facing), v = v(p, wall.facing);
                    minU = Math.min(minU, u);
                    maxU = Math.max(maxU, u);
                    minV = Math.min(minV, v);
                    maxV = Math.max(maxV, v);
                    for (Direction d : Direction.values()) {
                        if (d.getAxis() != wall.facing.getAxis() && left.remove(p.relative(d))) {
                            queue.add(p.relative(d));
                        }
                    }
                }
                set.frames.add(new Frame(wall.facing, wall.plane, minU, minV, maxU, maxV, wall.color, blocks));
            }
        }
        // cubes of one colour filling a whole box: one big cube; otherwise each on its own
        Map<BlockPos, Integer> left = new HashMap<>(cubes);
        while (!left.isEmpty()) {
            BlockPos start = left.keySet().iterator().next();
            int color = left.remove(start);
            List<BlockPos> blocks = new ArrayList<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                blocks.add(p);
                minX = Math.min(minX, p.getX());
                minY = Math.min(minY, p.getY());
                minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX());
                maxY = Math.max(maxY, p.getY());
                maxZ = Math.max(maxZ, p.getZ());
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    Integer c = left.get(n);
                    if (c != null && c == color) {
                        left.remove(n);
                        queue.add(n);
                    }
                }
            }
            long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (volume == blocks.size()) {
                set.boxes.add(box(level, minX, minY, minZ, maxX, maxY, maxZ, color, blocks));
            } else {
                for (BlockPos p : blocks) {
                    set.boxes.add(box(level, p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ(), color, List.of(p)));
                }
            }
        }
        return set;
    }

    private static Box box(BlockGetter level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int color, List<BlockPos> blocks) {
        List<Face> faces = new ArrayList<>();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (BlockPos p : blocks) {
            for (Direction d : Direction.values()) {
                n.setWithOffset(p, d);
                boolean inside = n.getX() >= minX && n.getX() <= maxX && n.getY() >= minY && n.getY() <= maxY
                        && n.getZ() >= minZ && n.getZ() <= maxZ;
                if (!inside && !level.getBlockState(n).isSolidRender(level, n)) {
                    faces.add(new Face(p, d));
                }
            }
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ, color, faces);
    }

    // ---- a wall's own coordinates: u across, v up (or along z on floors and ceilings) ----

    static int u(BlockPos pos, Direction facing) {
        return facing.getAxis() == Direction.Axis.X ? pos.getZ() : pos.getX();
    }

    static int v(BlockPos pos, Direction facing) {
        return facing.getAxis() == Direction.Axis.Y ? pos.getZ() : pos.getY();
    }

    static Vector3f axisU(Direction facing) {
        return facing.getAxis() == Direction.Axis.X ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0);
    }

    static Vector3f axisV(Direction facing) {
        return facing.getAxis() == Direction.Axis.Y ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
    }

    /** The point on the frame's wall at (u, v). */
    static Vec3 corner(Frame f, double u, double v) {
        return switch (f.facing.getAxis()) {
            case X -> new Vec3(f.plane, v, u);
            case Y -> new Vec3(u, f.plane, v);
            case Z -> new Vec3(u, v, f.plane);
        };
    }
}
