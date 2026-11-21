package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TrainEntity;
import com.example.skyblocks.TrainPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Using a train: right-click a car to sit in it (any of them: its whole length, turned along the rails, not only its
 * hit box), sneak + right-click (or right-click riding it) for its screen (client/TrainScreen).
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TrainClient {
    private TrainClient() {}

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null || mc.level == null) {
            return;
        }
        if (mc.player.getVehicle() instanceof TrainEntity t) {
            TrainScreen.open(t);
            cancel(event);
            return;
        }
        Vec3 from = mc.player.getEyePosition(), look = mc.player.getViewVector(1.0F);
        double reach = 5.0;
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) {
            reach = Math.min(reach, mc.hitResult.getLocation().distanceTo(from) + 0.05);
        }
        TrainEntity best = null;
        int bestUnit = -1;
        double bestT = reach;
        for (TrainEntity t : mc.level.getEntitiesOfClass(TrainEntity.class, new AABB(from, from).inflate(40.0))) {
            for (int k = 0; k < t.units(); k++) {
                Vec3[] u = t.unit(k, 1.0F);
                double hit = hit(from, look, u[0], u[1]);
                if (hit >= 0 && hit < bestT) {
                    bestT = hit;
                    best = t;
                    bestUnit = k;
                }
            }
        }
        if (best == null) {
            return;
        }
        if (mc.player.isShiftKeyDown()) {
            TrainScreen.open(best);
        } else {
            PacketDistributor.sendToServer(TrainPayload.of(best, TrainPayload.SIT, bestUnit));
        }
        cancel(event);
    }

    private static void cancel(InputEvent.InteractionKeyMappingTriggered event) {
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    /** Where a ray first meets a car's box (its middle, its way along the rails), or -1. */
    private static double hit(Vec3 from, Vec3 dir, Vec3 mid, Vec3 f) {
        Vec3 s = f.cross(new Vec3(0, 1, 0)).normalize(), u = s.cross(f);
        Vec3 o = from.subtract(mid);
        double[] ro = {o.dot(f), o.dot(u), o.dot(s)}, rd = {dir.dot(f), dir.dot(u), dir.dot(s)};
        double[] lo = {-2.6, 0.0, -1.0}, hi = {2.6, 2.6, 1.0};
        double t0 = 0, t1 = 1e9;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(rd[i]) < 1e-9) {
                if (ro[i] < lo[i] || ro[i] > hi[i]) return -1;
                continue;
            }
            double a = (lo[i] - ro[i]) / rd[i], b = (hi[i] - ro[i]) / rd[i];
            t0 = Math.max(t0, Math.min(a, b));
            t1 = Math.min(t1, Math.max(a, b));
        }
        return t1 >= t0 ? t0 : -1;
    }
}
