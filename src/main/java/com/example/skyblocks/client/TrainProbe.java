package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.StationBlockEntity;
import com.example.skyblocks.TrainEntity;
import com.example.skyblocks.TrainRoute;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Only for the CI run: trains put through their paces, written to the log (RenderTest). A train going round its loop
 * line (which stations it stops at, in order; its doors); driven (its speed with the throttle open, then braked). And
 * the player: walking in a car (along its corridor, into its side wall - stopped there, standing on its floor) and
 * carried in it as it goes.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TrainProbe {
    /** (the loop test's train, near here; how long it's been going) */
    static Vec3 loopAt;
    private static int loopTicks = -1;
    private static String lastStop = "";
    /** (the walking test: its stage, ticks into it) */
    static int walk = -1, carry = -1;

    private TrainProbe() {}

    static void startLoop(Vec3 at) {
        loopAt = at;
        loopTicks = 0;
    }

    private static TrainEntity nearest(net.minecraft.world.level.Level level, Vec3 at, double reach) {
        TrainEntity best = null;
        for (TrainEntity t : level.getEntitiesOfClass(TrainEntity.class, new AABB(at, at).inflate(reach))) {
            if (best == null || t.distanceToSqr(at) < best.distanceToSqr(at)) {
                best = t;
            }
        }
        return best;
    }

    private static String name(net.minecraft.world.level.Level level, BlockPos p) {
        return level.getBlockEntity(p) instanceof StationBlockEntity be ? be.name() : p.toShortString();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!RenderTest.ON) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        if (loopTicks >= 0) {
            int tk = loopTicks++;
            server.execute(() -> loopStep(server.overworld(), tk));
        }
        if (walk >= 0) {
            walkStep(mc, walk++);
        }
        if (carry >= 0) {
            carryStep(mc, server, carry++);
        }
    }

    private static void loopStep(ServerLevel level, int tk) {
        TrainEntity t = nearest(level, loopAt, 80);
        if (t == null) {
            if (tk % 100 == 0) SkyBlocksMod.LOGGER.info("Shadered+ train probe: no loop train");
            return;
        }
        if (tk == 5) {
            t.setLoop(true);
            StringBuilder sb = new StringBuilder();
            for (BlockPos p : t.order()) {
                sb.append(name(level, p)).append(" > ");
            }
            SkyBlocksMod.LOGGER.info("Shadered+ train probe: loop on, order {}", sb);
        }
        if (tk < 1100 && tk % 5 == 0 && !t.moving()) {
            for (BlockPos p : TrainRoute.allStations(level)) {
                Vec3 stop = TrainRoute.stopPoint(level, p);
                if (stop != null && (t.inside(stop.add(0, 1.5, 0)) >= 0 || t.end().distanceToSqr(stop) < 2.5)) {
                    String n = name(level, p);
                    if (!n.equals(lastStop)) {
                        lastStop = n;
                        SkyBlocksMod.LOGGER.info("Shadered+ train probe: tick {} stopped at {} (doors {})", tk, n, String.format("%.2f", t.doors(0)));
                    }
                }
            }
        }
        if (tk % 100 == 0) {
            SkyBlocksMod.LOGGER.info("Shadered+ train probe: tick {} at {} moving {} speed {} doors {}", tk, t.at(t.head(0)), t.moving(),
                    String.format("%.3f", t.speed(0)), String.format("%.2f", t.doors(0)));
        }
        // then driven: throttle full ahead from its cab, then brakes
        if (tk == 1100) {
            t.setLoop(false);
            t.throttle(0, 6, false);
            SkyBlocksMod.LOGGER.info("Shadered+ train probe: throttle 6");
        }
        if (tk > 1100 && tk < 1400 && tk % 20 == 0) {
            SkyBlocksMod.LOGGER.info("Shadered+ train probe: driving tick {} speed {} notch {} doors {}", tk, String.format("%.3f", t.speed(0)), t.notch(),
                    String.format("%.2f", t.doors(0)));
        }
        if (tk == 1260) {
            t.throttle(0, 0, true);
            SkyBlocksMod.LOGGER.info("Shadered+ train probe: brake");
        }
    }

    /** Walking in the branch train: stood in its middle car, walking along it, then into its side wall. */
    private static void walkStep(Minecraft mc, int tk) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        TrainEntity t = nearest(mc.level, new Vec3(1005, -59, 72), 60);
        if (t == null) {
            if (tk == 1) SkyBlocksMod.LOGGER.info("Shadered+ train probe: walk - no train");
            return;
        }
        TrainEntity.Frame f = t.solidFrames()[t.units() - 1];
        float along = (float) (Mth.atan2(-f.f().x, f.f().z) * Mth.RAD_TO_DEG);
        if (tk == 1) {
            Vec3 p = f.world(1.0, TrainEntity.FL + 0.02, 0.0);
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f 0", p.x, p.y, p.z, along));
        }
        if (tk == 15 || tk == 45) {
            mc.player.setYRot(tk == 15 ? along : along + 90.0F);
            mc.options.keyUp.setDown(true);
        }
        if (tk == 40 || tk == 70) {
            mc.options.keyUp.setDown(false);
        }
        if (tk % 5 == 0 && tk > 0 && tk <= 75) {
            Vec3 l = f.local(mc.player.position());
            SkyBlocksMod.LOGGER.info("Shadered+ train probe: walk tick {} local {} {} {} onGround {} inside {}", tk, String.format("%.2f", l.x),
                    String.format("%.2f", l.y), String.format("%.2f", l.z), mc.player.onGround(), t.inside(mc.player.position()));
        }
        if (tk > 80) {
            walk = -1;
        }
    }

    /** Carried: stood in the branch train, then it's sent off to another station; where in it the player stays. */
    private static void carryStep(Minecraft mc, MinecraftServer server, int tk) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        TrainEntity t = nearest(mc.level, mc.player.position(), 80);
        if (tk == 1) {
            TrainEntity b = nearest(mc.level, new Vec3(1005, -59, 72), 60);
            if (b == null) {
                carry = -1;
                return;
            }
            TrainEntity.Frame f = b.solidFrames()[b.units() - 1];
            Vec3 p = f.world(-1.0, TrainEntity.FL + 0.02, 0.0);
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.3f %.3f %.3f 0 0", p.x, p.y, p.z));
        }
        if (tk == 12) {
            server.execute(() -> {
                TrainEntity b = nearest(server.overworld(), new Vec3(1005, -59, 72), 60);
                if (b != null) {
                    SkyBlocksMod.LOGGER.info("Shadered+ train probe: carry - sent {}", b.goTo(new BlockPos(1023, -56, 128)));
                }
            });
        }
        if (t != null && tk >= 24 && tk <= 40) {
            TrainEntity.Frame[] cf = t.clientFrames;
            TrainEntity.Frame now = t.frameP(1, 0.0F);
            SkyBlocksMod.LOGGER.info("Shadered+ train probe: carry detail tick {} player {} vehicle {} head {} front {} snap1 {} now1 {} localSnap {} localNow {}", tk,
                    mc.player.position(), mc.player.getVehicle(), String.format("%.2f", t.head(0)), t.locoFront(),
                    cf == null || cf.length < 2 ? "-" : cf[1].mid(), now.mid(), cf == null || cf.length < 2 ? "-" : cf[1].local(mc.player.position()),
                    now.local(mc.player.position()));
        }
        if (t != null && tk % 5 == 0 && tk > 0) {
            int k = t.inside(mc.player.position());
            Vec3 l = k >= 0 ? t.solidFrames()[k].local(mc.player.position()) : Vec3.ZERO;
            SkyBlocksMod.LOGGER.info("Shadered+ train probe: carry tick {} unit {} local {} {} {} speed {} onGround {}", tk, k, String.format("%.2f", l.x),
                    String.format("%.2f", l.y), String.format("%.2f", l.z), String.format("%.3f", t.speed(0)), mc.player.onGround());
        }
        if (tk > 95) {
            carry = -1;
        }
    }
}
