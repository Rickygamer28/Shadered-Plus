package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Trains' walls, floors and seats as solid as blocks (mixin/TrainCollideMixin adds them to what a thing moving bumps
 * into): each piece of a car (TrainEntity.boxes) turned with it into the world - exactly, along a straight stretch
 * that runs along x or z; round corners, in short pieces. On the server, players aren't stopped (their own clients
 * are, a tick behind: the server would put them back where they'd been).
 */
public final class TrainCollision {
    private static final Map<Integer, List<float[]>> BOXES = new HashMap<>();

    private TrainCollision() {}

    private static List<float[]> boxes(int style, int kind, boolean plus, boolean minus, boolean open) {
        int key = style * 64 + kind * 8 + (plus ? 4 : 0) + (minus ? 2 : 0) + (open ? 1 : 0);
        synchronized (BOXES) {
            return BOXES.computeIfAbsent(key, k -> TrainEntity.boxes(style, kind, plus, minus, open ? 1.0F : 0.0F));
        }
    }

    /** The shapes of the trains near a thing moving through this space. */
    public static List<VoxelShape> shapes(Entity e, AABB region) {
        if (e instanceof TrainEntity || e.isSpectator() || e.isPassenger()) {
            return List.of();
        }
        boolean client = e.level().isClientSide;
        if (!client && e instanceof Player) {
            return List.of();
        }
        List<TrainEntity> near = new ArrayList<>();
        synchronized (TrainEntity.class) {
            for (TrainEntity t : client ? TrainEntity.CLIENT : TrainEntity.SERVER) {
                if (t.level() == e.level() && !t.isRemoved() && t.getBoundingBoxForCulling().intersects(region)) {
                    near.add(t);
                }
            }
        }
        if (near.isEmpty()) {
            return List.of();
        }
        List<VoxelShape> out = new ArrayList<>();
        Vec3 c = region.getCenter();
        double reach = TrainEntity.HL + 2.0 + region.getSize();
        for (TrainEntity t : near) {
            TrainEntity.Frame[] fr = t.solidFrames();
            boolean open = t.doors(0.0F) > 0.85F;
            for (int k = 0; k < fr.length; k++) {
                if (fr[k].mid().distanceToSqr(c) > reach * reach) {
                    continue;
                }
                for (float[] b : boxes(t.style(), t.kind(k), t.gangPlus(k), t.gangMinus(k), open)) {
                    add(out, fr[k], b, region);
                }
            }
        }
        return out;
    }

    private static void add(List<VoxelShape> out, TrainEntity.Frame f, float[] b, AABB region) {
        Vec3 d = f.f();
        boolean square = Math.abs(d.y) < 1e-3 && f.u().y > 0.9999 && (Math.abs(d.x) > 0.9999 || Math.abs(d.z) > 0.9999);
        if (square) {
            piece(out, f, b[0], b[1], b[2], b[3], b[4], b[5], region);
            return;
        }
        // (turned: cut along its length, and across if it's wide, into pieces half a block or less)
        int nx = Math.max(1, (int) Math.ceil((b[3] - b[0]) / 0.5F)), nz = Math.max(1, (int) Math.ceil((b[5] - b[2]) / 0.5F));
        for (int i = 0; i < nx; i++) {
            float x0 = b[0] + (b[3] - b[0]) * i / nx, x1 = b[0] + (b[3] - b[0]) * (i + 1) / nx;
            for (int j = 0; j < nz; j++) {
                float z0 = b[2] + (b[5] - b[2]) * j / nz, z1 = b[2] + (b[5] - b[2]) * (j + 1) / nz;
                piece(out, f, x0, b[1], z0, x1, b[4], z1, region);
            }
        }
    }

    private static void piece(List<VoxelShape> out, TrainEntity.Frame f, double x0, double y0, double z0, double x1, double y1, double z1, AABB region) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vec3 p = f.world((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1);
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            maxZ = Math.max(maxZ, p.z);
        }
        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        if (box.intersects(region)) {
            out.add(Shapes.create(box));
        }
    }
}
