package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Trains' walls, floors and seats as solid as blocks (mixin/TrainCollideMixin): a thing's move, once the world's blocks
 * have had their say, worked out again in each car's own space - its pieces (TrainEntity.boxes) square there - so walls
 * are exact however the car's turned (round curves, up slopes), and turned back. On the server, players aren't
 * stopped (their own clients are, a tick behind: the server would put them back where they'd been).
 */
public final class TrainCollision {
    private static final Map<Integer, List<VoxelShape>> SHAPES = new HashMap<>();

    private TrainCollision() {}

    private static List<VoxelShape> shapes(int style, int kind, boolean plus, boolean minus, boolean open) {
        int key = style * 64 + kind * 8 + (plus ? 4 : 0) + (minus ? 2 : 0) + (open ? 1 : 0);
        synchronized (SHAPES) {
            return SHAPES.computeIfAbsent(key, k -> {
                List<VoxelShape> out = new ArrayList<>();
                for (float[] b : TrainEntity.boxes(style, kind, plus, minus, open ? 1.0F : 0.0F)) {
                    out.add(Shapes.create(b[0], b[1], b[2], b[3], b[4], b[5]));
                }
                return out;
            });
        }
    }

    /** A thing's move (after blocks), cut short by the trains near it. */
    public static Vec3 collide(Entity e, Vec3 move) {
        if (e instanceof TrainEntity || e.isSpectator() || e.isPassenger() || e.noPhysics || move.lengthSqr() < 1e-12) {
            return move;
        }
        boolean client = e.level().isClientSide;
        if (!client && e instanceof Player) {
            return move;
        }
        AABB box = e.getBoundingBox();
        AABB region = box.expandTowards(move).inflate(1.0);
        List<TrainEntity> near = new ArrayList<>();
        synchronized (TrainEntity.class) {
            for (TrainEntity t : client ? TrainEntity.CLIENT : TrainEntity.SERVER) {
                if (t.level() == e.level() && !t.isRemoved() && t.getBoundingBoxForCulling().intersects(region)) {
                    near.add(t);
                }
            }
        }
        if (near.isEmpty()) {
            return move;
        }
        Vec3 c = box.getCenter();
        double hw = box.getXsize() / 2.0, hh = box.getYsize() / 2.0;
        for (TrainEntity t : near) {
            TrainEntity.Frame[] fr = t.solidFrames();
            boolean open = t.doors(0.0F) > 0.85F;
            for (int k = 0; k < fr.length; k++) {
                TrainEntity.Frame f = fr[k];
                Vec3 lc = f.local(c);
                if (Math.abs(lc.x) > TrainEntity.HL + 3.0 || Math.abs(lc.z) > TrainEntity.OW + 2.5 || lc.y < -3.0 || lc.y > TrainEntity.RT + 3.0) {
                    continue;
                }
                // (its move in the car's space; it, a box there the same size)
                Vec3 m = new Vec3(move.dot(f.f()), move.dot(f.u()), move.dot(f.s()));
                AABB lb = new AABB(lc.x - hw, lc.y - hh, lc.z - hw, lc.x + hw, lc.y + hh, lc.z + hw);
                List<VoxelShape> shapes = new ArrayList<>();
                AABB reach = lb.expandTowards(m).inflate(0.1);
                for (VoxelShape s : shapes(t.style(), t.kindP(k), t.gangPlusP(k), t.gangMinusP(k), open)) {
                    if (s.bounds().intersects(reach)) {
                        shapes.add(s);
                    }
                }
                if (shapes.isEmpty()) {
                    continue;
                }
                double y = Shapes.collide(Direction.Axis.Y, lb, shapes, m.y);
                lb = lb.move(0, y, 0);
                double x, z;
                if (Math.abs(m.x) >= Math.abs(m.z)) {
                    x = Shapes.collide(Direction.Axis.X, lb, shapes, m.x);
                    lb = lb.move(x, 0, 0);
                    z = Shapes.collide(Direction.Axis.Z, lb, shapes, m.z);
                } else {
                    z = Shapes.collide(Direction.Axis.Z, lb, shapes, m.z);
                    lb = lb.move(0, 0, z);
                    x = Shapes.collide(Direction.Axis.X, lb, shapes, m.x);
                }
                if (x != m.x || y != m.y || z != m.z) {
                    Vec3 w = f.f().scale(x).add(f.u().scale(y)).add(f.s().scale(z));
                    // (an axis it wasn't stopped on keeps the world's move exactly - no drift from turning it back and forth)
                    move = new Vec3(Math.abs(w.x - move.x) < 1e-7 ? move.x : w.x, Math.abs(w.y - move.y) < 1e-7 ? move.y : w.y,
                            Math.abs(w.z - move.z) < 1e-7 ? move.z : w.z);
                }
            }
        }
        return move;
    }
}
