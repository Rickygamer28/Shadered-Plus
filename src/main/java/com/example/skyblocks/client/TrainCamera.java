package com.example.skyblocks.client;

import com.example.skyblocks.TrainEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The third-person camera and trains (mixin/CameraTrainMixin). Riding one: the camera circles the car you're in, far
 * enough out to see it whole (and the cars either side), turning with your view - stopped short of blocks. Standing in
 * one: kept inside the car (not out through its walls, where you'd see only its outside).
 */
public final class TrainCamera {
    /** How far out the camera rides from a car's middle (blocks). */
    private static final double RIDING = 15.0;

    private TrainCamera() {}

    @Nullable
    public static Vec3 place(Camera camera, BlockGetter level, Entity entity, Vec3 was, float partialTick) {
        if (entity.getVehicle() instanceof TrainEntity t) {
            Integer key = t.seatOf(entity);
            int unit = key == null ? t.locoUnit() : Math.min(key / 16, t.units() - 1);
            TrainEntity.Frame f = t.frame(unit, partialTick);
            Vec3 pivot = f.world(0.0, TrainEntity.CE * 0.7, 0.0);
            Vec3 look = new Vec3(camera.getLookVector());
            Vec3 want = pivot.subtract(look.scale(RIDING));
            BlockHitResult hit = level.clip(new ClipContext(pivot, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
            if (hit.getType() != HitResult.Type.MISS) {
                want = hit.getLocation().add(look.scale(0.3));
            }
            return want;
        }
        // standing in a car: the camera kept within it
        java.util.List<TrainEntity> trains;
        synchronized (TrainEntity.class) {
            trains = new java.util.ArrayList<>(TrainEntity.CLIENT);
        }
        for (TrainEntity t : trains) {
            if (t.level() != entity.level() || t.isRemoved()) {
                continue;
            }
            Vec3 eye = entity.getEyePosition(partialTick);
            int k = t.inside(entity.position());
            if (k < 0) {
                continue;
            }
            TrainEntity.Frame f = t.frame(k, partialTick);
            Vec3 a = f.local(eye), b = f.local(was);
            double[] lo = {-TrainEntity.HL - 0.3, TrainEntity.FL + 0.1, -TrainEntity.IW + 0.15};
            double[] hi = {TrainEntity.HL + 0.3, TrainEntity.CE - 0.1, TrainEntity.IW - 0.15};
            double[] o = {a.x, a.y, a.z}, d = {b.x - a.x, b.y - a.y, b.z - a.z};
            double tExit = 1.0;
            for (int i = 0; i < 3; i++) {
                if (Math.abs(d[i]) < 1e-9) {
                    continue;
                }
                double t1 = ((d[i] > 0 ? hi[i] : lo[i]) - o[i]) / d[i];
                tExit = Math.min(tExit, t1);
            }
            if (tExit < 1.0) {
                double keep = Math.max(0.0, tExit - 0.05);
                return eye.add(was.subtract(eye).scale(keep));
            }
            return null;
        }
        return null;
    }
}
