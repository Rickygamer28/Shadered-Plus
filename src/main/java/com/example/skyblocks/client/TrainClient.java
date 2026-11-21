package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TrainEntity;
import com.example.skyblocks.TrainPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * You and trains: standing in one you're carried along with it (its cars as they were at the end of each tick: what
 * you bump into, TrainCollision); right-click a seat to sit; in a cab's driver's seat, W and S move its throttle a
 * notch at a time, Space brakes, its speed on the screen; right-click riding (or sneak + right-click in it) for its
 * screen (client/TrainScreen).
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TrainClient {
    private static int held;
    private static boolean braking, faced;

    /** Standing in a train: which car, and where in it you were last tick and are this one (its own frame). */
    @org.jetbrains.annotations.Nullable
    private static TrainEntity standIn;
    private static int standUnit;
    private static Vec3 standWas = Vec3.ZERO, standNow = Vec3.ZERO;

    private TrainClient() {}

    /**
     * Where you are now, standing in a moving train, as it's drawn this frame: the car as drawn (between ticks), you in
     * it - not a tick behind it, as you'd be drawn otherwise (and so jolting about in it as it speeds, slows and turns).
     */
    @org.jetbrains.annotations.Nullable
    public static Vec3 standing(net.minecraft.world.entity.Entity who, float partialTick) {
        TrainEntity t = standIn;
        if (t == null || who != Minecraft.getInstance().player || who.isPassenger() || t.isRemoved() || standUnit >= t.units()) {
            return null;
        }
        Vec3 l = standWas.lerp(standNow, partialTick);
        return t.frameP(standUnit, partialTick).world(l.x, l.y, l.z);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        LocalPlayer me = mc.player;
        boolean carried = false;
        List<TrainEntity> trains;
        synchronized (TrainEntity.class) {
            trains = new ArrayList<>(TrainEntity.CLIENT);
        }
        for (TrainEntity t : trains) {
            if (t.level() != mc.level || t.isRemoved()) {
                continue;
            }
            TrainEntity.Frame[] was = t.clientFrames;
            TrainEntity.Frame[] now = t.framesP(0.0F);
            t.clientFrames = now;
            // you, standing in it: carried from where you were in it to the same place now
            if (me != null && !me.isPassenger() && !me.isSpectator() && was != null && was.length == now.length) {
                for (int k = 0; k < now.length; k++) {
                    Vec3 l = was[k].local(me.position());
                    if (TrainEntity.within(l)) {
                        Vec3 p = now[k].world(l.x, l.y, l.z);
                        me.setPos(p.x, p.y, p.z);
                        // (where you were in it a tick ago, and now: the camera drawn from these, with the car as drawn)
                        Vec3 lo = was[k].local(new Vec3(me.xo, me.yo, me.zo));
                        boolean same = standIn == t && standUnit == k && TrainEntity.within(lo);
                        standIn = t;
                        standUnit = k;
                        standWas = same ? lo : l;
                        standNow = l;
                        carried = true;
                        float turn = Mth.wrapDegrees(now[k].yaw() - was[k].yaw());
                        me.setYRot(me.getYRot() + turn);
                        me.setYHeadRot(me.getYHeadRot() + turn);
                        me.fallDistance = 0.0F;
                        break;
                    }
                }
            }
        }
        if (!carried) {
            standIn = null;
        }
        if (me != null && me.getVehicle() instanceof TrainEntity t) {
            // (just sat down: facing the way the seat does)
            Integer key = t.seatOf(me);
            if (!faced && key != null) {
                faced = true;
                int unit = Math.min(key / 16, t.units() - 1);
                Vec3 f = t.frameP(unit, 0.0F).f().scale(TrainEntity.seatFacing(t.style(), t.kindP(unit), key % 16));
                float yaw = (float) (Mth.atan2(-f.x, f.z) * Mth.RAD_TO_DEG);
                me.setYRot(yaw);
                me.yRotO = yaw;
                me.setYHeadRot(yaw);
                me.setXRot(5.0F);
            }
            drive(mc, me, t);
        } else {
            held = 0;
            faced = false;
        }
    }

    /** Whether you're in a cab's driver's seat (its unit), or -1. */
    static int driving(TrainEntity t, LocalPlayer me) {
        Integer key = t.seatOf(me);
        if (key == null) {
            return -1;
        }
        int unit = key / 16;
        return unit < t.units() && TrainEntity.driverSeat(t.kindP(unit), key % 16) ? unit : -1;
    }

    private static void drive(Minecraft mc, LocalPlayer me, TrainEntity t) {
        int unit = driving(t, me);
        if (unit < 0 || mc.screen != null) {
            return;
        }
        boolean up = mc.options.keyUp.isDown(), down = mc.options.keyDown.isDown(), brake = mc.options.keyJump.isDown();
        if (brake) {
            if (!braking) {
                PacketDistributor.sendToServer(TrainPayload.of(t, TrainPayload.THROTTLE, unit * 64 + 32 + 0x1000));
            }
            braking = true;
            return;
        }
        braking = false;
        int d = up ? 1 : down ? -1 : 0;
        if (d == 0) {
            held = 0;
            return;
        }
        // (a notch on the press, then one every 6 ticks held)
        if (held % 6 == 0) {
            int n = Mth.clamp(t.notch() + d, TrainEntity.NOTCH_MIN, TrainEntity.NOTCH_MAX);
            if (n != t.notch()) {
                PacketDistributor.sendToServer(TrainPayload.of(t, TrainPayload.THROTTLE, unit * 64 + 32 + n));
            }
        }
        held++;
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof TrainEntity t) || driving(t, mc.player) < 0) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), y = g.guiHeight() - 72;
        int kmh = Math.round(t.speed(event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 20.0F * 3.6F);
        int n = t.notch();
        g.fill(w / 2 - 92, y - 4, w / 2 + 92, y + 26, 0xA0101820);
        g.drawCenteredString(mc.font, Component.translatable("hud.skyblocks.train.speed", kmh), w / 2, y, 0x8FEFFF);
        // the throttle: notches back (amber) and ahead (cyan)
        for (int i = TrainEntity.NOTCH_MIN; i <= TrainEntity.NOTCH_MAX; i++) {
            if (i == 0) {
                continue;
            }
            int x = w / 2 + i * 12 - 6;
            boolean on = i > 0 ? n >= i : n <= i;
            g.fill(x, y + 12, x + 10, y + 20, on ? (i > 0 ? 0xFF4FE0FF : 0xFFFFB040) : 0xFF303A44);
        }
        g.fill(w / 2 - 1, y + 11, w / 2 + 1, y + 21, 0xFFFFFFFF);
        g.drawCenteredString(mc.font, Component.translatable("hud.skyblocks.train.keys"), w / 2, y + 30, 0xA0B0B8);
        // (a train or a claim on the track ahead: a red light)
        java.util.List<Vec3> ahead = new java.util.ArrayList<>();
        float h = t.head(0.0F), v = t.velocity(0.0F);
        for (int i = 2; i < 40; i += 2) {
            ahead.add(v < 0 ? t.at(h - t.length() - i) : t.at(h + i));
        }
        if (com.example.skyblocks.TrainSignals.others(mc.level, t, true).first(ahead) >= 0) {
            g.drawCenteredString(mc.font, Component.translatable("hud.skyblocks.train.blocked"), w / 2, y - 14, 0xFF6050);
        }
    }

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
        double reach = 4.5;
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) {
            reach = Math.min(reach, mc.hitResult.getLocation().distanceTo(from) + 0.3);
        }
        List<TrainEntity> trains;
        synchronized (TrainEntity.class) {
            trains = new ArrayList<>(TrainEntity.CLIENT);
        }
        // a seat you're looking at (along your look, every tenth of a block), or the train itself
        TrainEntity hitTrain = null;
        int hitKey = -1;
        for (double d = 0.0; d <= reach && hitKey < 0; d += 0.1) {
            Vec3 p = from.add(look.scale(d));
            for (TrainEntity t : trains) {
                if (t.level() != mc.level || t.isRemoved() || !t.getBoundingBoxForCulling().contains(p)) {
                    continue;
                }
                TrainEntity.Frame[] fr = t.solidFrames();
                for (int k = 0; k < fr.length && hitKey < 0; k++) {
                    Vec3 l = fr[k].local(p);
                    if (Math.abs(l.x) > TrainEntity.HL + 1.0 || Math.abs(l.z) > TrainEntity.OW + 0.1 || l.y < 0.0 || l.y > TrainEntity.RT) {
                        continue;
                    }
                    if (hitTrain == null) {
                        hitTrain = t;
                    }
                    float[][] seats = TrainEntity.seatsOf(t.style(), t.kindP(k));
                    for (int s = 0; s < seats.length; s++) {
                        if (Math.abs(l.x - seats[s][0]) < 0.45 && Math.abs(l.z - seats[s][1]) < 0.5 && l.y < TrainEntity.FL + 1.4) {
                            hitTrain = t;
                            hitKey = k * 16 + s;
                            break;
                        }
                    }
                }
            }
        }
        if (hitTrain == null) {
            return;
        }
        if (mc.player.isShiftKeyDown()) {
            TrainScreen.open(hitTrain);
            cancel(event);
        } else if (hitKey >= 0) {
            PacketDistributor.sendToServer(TrainPayload.of(hitTrain, TrainPayload.SIT, hitKey));
            cancel(event);
        }
    }

    private static void cancel(InputEvent.InteractionKeyMappingTriggered event) {
        event.setSwingHand(false);
        event.setCanceled(true);
    }
}
