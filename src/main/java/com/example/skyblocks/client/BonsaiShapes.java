package com.example.skyblocks.client;

import com.example.skyblocks.BonsaiTrees;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The bonsai's shaped styles as blocks, a block to a pixel of the pot (designed and previewed with
 * tools/bonsai/design.py, which builds them the same way): a flared root, a tapering trunk and branches of logs (each
 * lying the way its stretch runs), and foliage pads - flat underneath, domed on top, their edges a little uneven - of
 * leaves. Built of whatever tree's logs and leaves (any mod's), here and there flowering leaves on one that has them.
 * Coordinates: x right, y up from the soil, z towards the pot's front; the trunk at the middle.
 */
final class BonsaiShapes {
    private BonsaiShapes() {}

    /** A shape: its blocks (0 leaves; 1, 2, 3 a log lying along x, y, z) and where fruit hangs (under its pads). */
    static final class Shape {
        final Map<BlockPos, Integer> blocks = new HashMap<>();
        final List<float[]> fruitSpots = new ArrayList<>();

        private void log(int x, int y, int z, int axis) {
            blocks.put(new BlockPos(x, y, z), axis);
        }

        private void leaf(int x, int y, int z) {
            blocks.putIfAbsent(new BlockPos(x, y, z), 0);
        }

        /** A trunk or branch through the points, its radius going from r0 to r1. */
        void limb(float[][] pts, float r0, float r1) {
            float total = 0;
            for (int i = 0; i < pts.length - 1; i++) {
                total += dist(pts[i], pts[i + 1]);
            }
            float done = 0;
            for (int i = 0; i < pts.length - 1; i++) {
                float[] a = pts[i], b = pts[i + 1];
                float len = dist(a, b);
                float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
                int axis = Math.abs(dy) >= Math.abs(dx) && Math.abs(dy) >= Math.abs(dz) ? 2 : Math.abs(dx) >= Math.abs(dz) ? 1 : 3;
                int steps = Math.max(2, (int) (len * 4));
                for (int k = 0; k <= steps; k++) {
                    float t = k / (float) steps;
                    float px = a[0] + dx * t, py = a[1] + dy * t, pz = a[2] + dz * t;
                    float f = (done + len * t) / Math.max(total, 1.0E-6F);
                    float r = r0 + (r1 - r0) * f;
                    int reach = (int) Math.ceil(r) + 1;
                    for (int ox = -reach; ox <= reach; ox++) {
                        for (int oy = -reach; oy <= reach; oy++) {
                            for (int oz = -reach; oz <= reach; oz++) {
                                int bx = (int) Math.floor(px + ox), by = (int) Math.floor(py + oy), bz = (int) Math.floor(pz + oz);
                                float cx = bx + 0.5F - px, cy = by + 0.5F - py, cz = bz + 0.5F - pz;
                                if (cx * cx + cy * cy + cz * cz <= r * r + 0.15F) {
                                    log(bx, by, bz, axis);
                                }
                            }
                        }
                    }
                }
                done += len;
            }
        }

        /** A foliage pad round (cx, cz), its underside at cy, h blocks deep. */
        void pad(float cx, float cy, float cz, float rx, float rz, int h, int seed) {
            int base = (int) Math.floor(cy);
            for (int k = 0; k < h; k++) {
                int y = base + k;
                float shrink = 1.0F - (float) Math.pow(k / (float) Math.max(1, h), 1.6) * 0.75F;
                float ax = rx * shrink, az = rz * shrink;
                for (int x = (int) Math.floor(cx - rx - 1); x <= (int) Math.ceil(cx + rx + 1); x++) {
                    for (int z = (int) Math.floor(cz - rz - 1); z <= (int) Math.ceil(cz + rz + 1); z++) {
                        float ex = (x + 0.5F - cx) / ax, ez = (z + 0.5F - cz) / az;
                        if (ex * ex + ez * ez <= 0.78F + 0.35F * hash(x, y, z, seed)) {
                            leaf(x, y, z);
                        }
                    }
                }
            }
            fruitSpots.add(new float[] {cx, base - 0.8F, cz});
        }

        /** The flare of roots at the soil. */
        void rootFlare(float r) {
            for (int x = -2; x < 2; x++) {
                for (int z = -2; z < 2; z++) {
                    float cx = x + 0.5F, cz = z + 0.5F;
                    if (cx * cx + cz * cz <= r * r) {
                        log(x, 0, z, 2);
                    }
                }
            }
        }
    }

    private static float dist(float[] a, float[] b) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float hash(int x, int y, int z, int seed) {
        int n = x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ seed * 0x9E3779B1;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0xFFFF) / 65535.0F;
    }

    private static float[] p(float x, float y, float z) {
        return new float[] {x, y, z};
    }

    /** One for each shaped BonsaiBlock.Style: upright, round, windswept, cascade, cloud pruned. */
    static final Shape[] SHAPES = {upright(), round(), windswept(), cascade(), clouds()};

    private static Shape upright() {
        Shape s = new Shape();
        s.rootFlare(2.0F);
        s.limb(new float[][] {p(0, 0, 0), p(0, 6, 0), p(0.2F, 11, 0)}, 1.35F, 0.55F);
        s.limb(new float[][] {p(0, 3.5F, 0), p(-2.5F, 4.5F, 0.5F), p(-4, 5, 0.5F)}, 0.55F, 0.5F);
        s.limb(new float[][] {p(0, 5.5F, 0), p(2.5F, 6.6F, -0.5F), p(3.8F, 7, -0.5F)}, 0.5F, 0.5F);
        s.limb(new float[][] {p(0, 7.5F, 0), p(-1.5F, 8.6F, -1.5F)}, 0.5F, 0.5F);
        s.pad(-4.2F, 5, 0.6F, 3.0F, 2.4F, 2, 1);
        s.pad(3.8F, 7, -0.6F, 2.7F, 2.2F, 2, 2);
        s.pad(-1.6F, 8.8F, -1.6F, 2.0F, 1.8F, 2, 3);
        s.pad(0.2F, 10.6F, 0, 2.4F, 2.2F, 3, 4);
        return s;
    }

    private static Shape round() {
        Shape s = new Shape();
        s.rootFlare(2.0F);
        s.limb(new float[][] {p(0, 0, 0), p(0, 4, 0)}, 1.35F, 1.0F);
        for (float[] b : new float[][] {{0, 7}, {2.1F, 7.5F}, {4.2F, 7}}) {
            s.limb(new float[][] {p(0, 3.5F, 0), p((float) Math.cos(b[0]) * 2.2F, b[1], (float) Math.sin(b[0]) * 2.2F)}, 0.75F, 0.5F);
        }
        s.pad(0, 6.5F, 0, 4.6F, 4.0F, 4, 5);
        s.pad(0, 9.5F, 0, 3.2F, 2.8F, 2, 6);
        return s;
    }

    private static Shape windswept() {
        Shape s = new Shape();
        s.rootFlare(2.0F);
        s.limb(new float[][] {p(-0.5F, 0, 0), p(0.5F, 3.5F, 0), p(2.8F, 6.5F, 0.2F), p(5.2F, 8.6F, 0.2F)}, 1.4F, 0.55F);
        s.limb(new float[][] {p(0.5F, 3.5F, 0), p(-1.6F, 5.2F, 0.8F), p(-3.0F, 5.6F, 0.8F)}, 0.55F, 0.5F);
        s.limb(new float[][] {p(2.8F, 6.5F, 0.2F), p(3.4F, 7.4F, -1.8F)}, 0.5F, 0.5F);
        s.limb(new float[][] {p(2.0F, 5.4F, 0.2F), p(3.6F, 5.6F, 1.8F)}, 0.5F, 0.5F);
        s.pad(5.8F, 8.4F, 0.2F, 3.4F, 2.5F, 3, 7);
        s.pad(3.6F, 7.2F, -2.0F, 2.6F, 1.9F, 2, 8);
        s.pad(3.9F, 5.4F, 2.0F, 2.4F, 1.8F, 2, 16);
        s.pad(-3.0F, 5.6F, 0.8F, 2.0F, 1.7F, 2, 9);
        return s;
    }

    private static Shape cascade() {
        Shape s = new Shape();
        s.rootFlare(1.8F);
        s.limb(new float[][] {p(0, 0, -1), p(0.3F, 3, -0.3F), p(0.4F, 3.6F, 2.2F), p(0.3F, 2, 4.6F), p(0, -0.5F, 5.6F), p(-0.2F, -1.6F, 6.0F)},
                1.25F, 0.5F);
        s.pad(0.4F, 4, 0.2F, 2.6F, 2.4F, 2, 10);
        s.pad(0.3F, 1.2F, 4.9F, 2.4F, 1.8F, 2, 11);
        s.pad(-0.2F, -2.6F, 6.3F, 2.6F, 2.0F, 2, 12);
        return s;
    }

    private static Shape clouds() {
        Shape s = new Shape();
        s.rootFlare(2.0F);
        s.limb(new float[][] {p(0, 0, 0), p(1.3F, 3, 0), p(-1.1F, 6.2F, 0.3F), p(0.5F, 9.4F, 0)}, 1.35F, 0.55F);
        s.limb(new float[][] {p(1.3F, 3.2F, 0), p(3.4F, 4.6F, 0.6F), p(4.2F, 5.2F, 0.6F)}, 0.55F, 0.5F);
        s.limb(new float[][] {p(-1.1F, 6.2F, 0.3F), p(-3.4F, 7.2F, -0.6F)}, 0.5F, 0.5F);
        s.limb(new float[][] {p(-0.2F, 7.6F, 0.1F), p(1.4F, 8.0F, -2.2F)}, 0.5F, 0.5F);
        s.pad(4.3F, 5.2F, 0.6F, 2.1F, 2.1F, 3, 13);
        s.pad(-3.7F, 7.2F, -0.6F, 2.1F, 2.1F, 3, 14);
        s.pad(1.5F, 8.0F, -2.4F, 1.7F, 1.7F, 2, 17);
        s.pad(0.5F, 9.6F, 0, 2.5F, 2.5F, 3, 15);
        return s;
    }

    /**
     * A shape in this tree's blocks (at BonsaiTrees.GROUND, as a grown tree is): its logs lying the way they run, its
     * leaves, some flowering. What's under the soil inside the pot is left out (nobody sees it).
     */
    static Map<BlockPos, BlockState> blocks(Shape shape, BlockState log, BlockState leaves, BlockState flowering) {
        Map<BlockPos, BlockState> out = new HashMap<>();
        int ground = BonsaiTrees.GROUND;
        for (Map.Entry<BlockPos, Integer> e : shape.blocks.entrySet()) {
            BlockPos p = e.getKey();
            if (p.getY() < 0 && Math.abs(p.getX() + 0.5F) < 5 && Math.abs(p.getZ() + 0.5F) < 4) {
                continue;
            }
            int kind = e.getValue();
            BlockState st;
            if (kind == 0) {
                boolean bloom = flowering != null && Math.floorMod(p.getX() * 73856093 ^ p.getY() * 19349663 ^ p.getZ() * 83492791, 5) < 2;
                st = bloom ? flowering : leaves;
            } else {
                Direction.Axis axis = kind == 1 ? Direction.Axis.X : kind == 2 ? Direction.Axis.Y : Direction.Axis.Z;
                st = log.hasProperty(BlockStateProperties.AXIS) ? log.setValue(BlockStateProperties.AXIS, axis) : log;
            }
            out.put(new BlockPos(p.getX(), p.getY() + ground, p.getZ()), st);
        }
        return out;
    }
}
