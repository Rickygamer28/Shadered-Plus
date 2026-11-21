package com.example.skyblocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A train: a locomotive and its cars (1 .. 4) - each 12 blocks long, 3 wide inside, a corridor between rows of seats,
 * sliding doors in its sides (open whenever it's stopped), gangways between the cars - running along a line of train
 * rails (TrainLine). Its walls are solid (TrainCollision): you walk in through its doors, and stand in it as it goes
 * (carried along: client/TrainClient for you, here for mobs and dropped things).
 *
 * Where it is comes from its trip, known alike to the server and every client: the way along the rails (points) and
 * how it moves along it - sent to a station, smoothly there and stopping (eased); driven from a cab, speeding up or
 * slowing towards the speed its throttle's set to (drive). Each car follows the way at its own distance behind its
 * front, turned along it. A maglev has a cab at each end (driven from either); a steam train its locomotive in front.
 */
public class TrainEntity extends Entity {
    // ---- its size (blocks; a car's own space: x along it - its nose +x - y up from the rails' middle, z across) ----
    /** A car's length and the gap after it; its body's length, half of it. */
    public static final float CAR = 12.6F, BODY = 12.0F, HL = 6.0F;
    /** Its floor's top; inside, half its width; outside, half its width; its ceiling; its roof's top. */
    public static final float FL = 0.9F, IW = 1.5F, OW = 1.65F, CE = 4.1F, RT = 4.35F;
    /** Its doors: their middles along it (a cab car's only the one at -x), half their width, their height. */
    public static final float DOOR_X = 3.6F, DOOR_HALF = 0.7F, DOOR_H = 2.4F;
    /** The gangways between cars: half their width, their height, how far each car's half reaches out. */
    public static final float GANG_HALF = 0.6F, GANG_H = 2.3F, GANG_OUT = 0.33F;

    public static final String[] STYLES = {"maglev", "steam"};
    /** Top speed (blocks a tick), and how fast it speeds up and slows (blocks a tick, a tick), by look. */
    private static final float[] TOP = {1.2F, 0.8F};
    private static final float[] ACC = {0.006F, 0.004F};
    private static final float BRAKE = 0.03F;
    /** Throttle notches: back up to -3, ahead up to 6. */
    public static final int NOTCH_MIN = -3, NOTCH_MAX = 6;
    /** How long it waits at a station going round; how long its doors take to shut before it sets off (ticks). */
    private static final int WAIT = 100, SHUT = 16;

    private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CARS = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LOOP = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> NOTCH = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    /**
     * Its trip: pts (the way: x y z ..., float bits), front (its locomotive at the way's far end), start (game time), mode
     * (0 eased: from, to, time, delay; 1 drive: s0, v0, vt, acc, delay, lo, hi - where it must stop - and openLo/openHi,
     * the line going on past them), dw (how open its doors were as it began).
     */
    private static final EntityDataAccessor<CompoundTag> TRIP = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** The stations it passes by going round (their places): stopped at, all the others. */
    private static final EntityDataAccessor<CompoundTag> SKIP = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** The stations it goes round, in order (p: their places; custom: set by hand, not worked out). */
    private static final EntityDataAccessor<CompoundTag> ORDER = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** Who sits where (ids, keys: unit * 16 + seat). */
    private static final EntityDataAccessor<CompoundTag> SEATS = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.COMPOUND_TAG);

    /** The trains in the worlds now, client and server (TrainCollision looks through them). */
    public static final Set<TrainEntity> CLIENT = Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public static final Set<TrainEntity> SERVER = Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private CompoundTag parsedFrom;
    private float[] pts = new float[0];
    private float[] cum = new float[0];
    private final Map<Integer, Integer> seats = new HashMap<>();
    /** (server) When it got where it was going (going round); whether it was moving last tick; its cars, last tick. */
    private long arrivedAt = Long.MIN_VALUE;
    private boolean wasMoving;
    private Frame[] lastFrames;
    /** (client) Its cars as they were at the end of the last tick: what you collide with, and are carried by. */
    public Frame[] clientFrames;

    public TrainEntity(EntityType<? extends TrainEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STYLE, 0);
        builder.define(CARS, 2);
        builder.define(LOOP, false);
        builder.define(NOTCH, 0);
        builder.define(TRIP, new CompoundTag());
        builder.define(SEATS, new CompoundTag());
        builder.define(SKIP, new CompoundTag());
        builder.define(ORDER, new CompoundTag());
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        synchronized (TrainEntity.class) {
            (level().isClientSide ? CLIENT : SERVER).add(this);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        synchronized (TrainEntity.class) {
            (level().isClientSide ? CLIENT : SERVER).remove(this);
        }
    }

    // ---- what it is ----

    public int style() {
        return Math.floorMod(entityData.get(STYLE), STYLES.length);
    }

    public int cars() {
        return entityData.get(CARS);
    }

    public int units() {
        return cars() + 1;
    }

    public boolean loop() {
        return entityData.get(LOOP);
    }

    public int notch() {
        return entityData.get(NOTCH);
    }

    public void setStyle(int s) {
        entityData.set(STYLE, Math.floorMod(s, STYLES.length));
    }

    public void setCars(int n) {
        entityData.set(CARS, Mth.clamp(n, 1, 4));
    }

    public void setLoop(boolean on) {
        entityData.set(LOOP, on);
        arrivedAt = level().getGameTime() - WAIT + 20;
        if (on && !level().isClientSide && !customOrder()) {
            setOrder(defaultOrder(), false); // (worked out afresh: where it is now)
        }
    }

    public float length() {
        return units() * CAR;
    }

    /** The stations it goes round, in order. */
    public List<BlockPos> order() {
        List<BlockPos> out = new ArrayList<>();
        for (long l : entityData.get(ORDER).getLongArray("p")) {
            out.add(BlockPos.of(l));
        }
        return out;
    }

    /** Whether its order was set by hand. */
    public boolean customOrder() {
        return entityData.get(ORDER).getBoolean("custom");
    }

    public void setOrder(List<BlockPos> stations, boolean custom) {
        CompoundTag t = new CompoundTag();
        t.putLongArray("p", stations.stream().limit(64).mapToLong(BlockPos::asLong).toArray());
        t.putBoolean("custom", custom);
        entityData.set(ORDER, t);
        nextStop = 0;
    }

    /** Its order worked out: the station it's at, then the ones on ahead, nearest first, then the rest of its network. */
    public List<BlockPos> defaultOrder() {
        List<BlockPos> out = new ArrayList<>();
        List<List<BlockPos>> ways = stations();
        for (List<BlockPos> w : ways) {
            for (BlockPos p : w) {
                if (!out.contains(p)) {
                    out.add(p);
                }
            }
        }
        for (BlockPos p : TrainRoute.stationsOn(level(), at(head(0.0F)))) {
            if (!out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** (server) Which of its order it goes to next. */
    private int nextStop;

    /** Whether it passes a station by going round (not stopping there). */
    public boolean skips(BlockPos station) {
        for (long l : entityData.get(SKIP).getLongArray("p")) {
            if (l == station.asLong()) {
                return true;
            }
        }
        return false;
    }

    /** Stops at a station going round, or passes it by. */
    public void setSkip(BlockPos station, boolean skip) {
        long[] was = entityData.get(SKIP).getLongArray("p");
        java.util.List<Long> out = new ArrayList<>();
        for (long l : was) {
            if (l != station.asLong()) {
                out.add(l);
            }
        }
        if (skip && out.size() < 256) {
            out.add(station.asLong());
        }
        CompoundTag t = new CompoundTag();
        t.putLongArray("p", out.stream().mapToLong(Long::longValue).toArray());
        entityData.set(SKIP, t);
    }

    /** Its top speed (blocks a tick). */
    public float top() {
        return TOP[style()];
    }

    // ---- its trip ----

    private CompoundTag trip() {
        return entityData.get(TRIP);
    }

    private void parse() {
        CompoundTag t = trip();
        if (t == parsedFrom) {
            return;
        }
        parsedFrom = t;
        int[] bits = t.getIntArray("pts");
        int n = bits.length / 3;
        pts = new float[n * 3];
        cum = new float[n];
        for (int i = 0; i < n * 3; i++) {
            pts[i] = Float.intBitsToFloat(bits[i]);
        }
        for (int i = 1; i < n; i++) {
            float dx = pts[i * 3] - pts[i * 3 - 3], dy = pts[i * 3 + 1] - pts[i * 3 - 2], dz = pts[i * 3 + 2] - pts[i * 3 - 1];
            cum[i] = cum[i - 1] + Mth.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    public float total() {
        parse();
        return cum.length == 0 ? 0.0F : cum[cum.length - 1];
    }

    /** A place on the way, this far along it (beyond its ends: on along its first or last stretch). */
    public Vec3 at(float s) {
        parse();
        int n = cum.length;
        if (n == 0) {
            return position();
        }
        if (n == 1) {
            return new Vec3(pts[0], pts[1], pts[2]);
        }
        int lo, hi;
        if (s <= 0) {
            lo = 0;
            hi = 1;
        } else if (s >= cum[n - 1]) {
            lo = n - 2;
            hi = n - 1;
        } else {
            lo = 0;
            hi = n - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (cum[mid] <= s) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
        }
        float f = (s - cum[lo]) / Math.max(cum[hi] - cum[lo], 1e-6F);
        return new Vec3(Mth.lerp(f, pts[lo * 3], pts[hi * 3]), Mth.lerp(f, pts[lo * 3 + 1], pts[hi * 3 + 1]), Mth.lerp(f, pts[lo * 3 + 2], pts[hi * 3 + 2]));
    }

    private long seenStart = Long.MIN_VALUE;
    private int tripFrom;

    /** How long since its trip began (ticks): the server by game time, a client by its own ticks (smooth). */
    private float elapsed(float partialTick) {
        long start = trip().getLong("start");
        if (!level().isClientSide) {
            return level().getGameTime() - start + partialTick;
        }
        if (start != seenStart) {
            seenStart = start;
            tripFrom = tickCount - (int) Mth.clamp(level().getGameTime() - start, 0L, 1_000_000L);
        }
        return tickCount - tripFrom + partialTick;
    }

    private static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    public boolean driven() {
        return trip().getInt("mode") == 1;
    }

    /** How far along the way its front is, at this time into its trip. */
    private float headAt(float e) {
        CompoundTag t = trip();
        e -= t.getInt("delay");
        if (t.getInt("mode") == 1) {
            float s0 = t.getFloat("s0"), v0 = t.getFloat("v0"), vt = t.getFloat("vt"), a = Math.max(t.getFloat("acc"), 1e-5F);
            if (e <= 0) {
                return s0;
            }
            float T = Math.abs(vt - v0) / a, sg = Math.signum(vt - v0);
            float s = e < T ? s0 + v0 * e + 0.5F * sg * a * e * e : s0 + v0 * T + 0.5F * sg * a * T * T + vt * (e - T);
            return Mth.clamp(s, t.getFloat("lo"), t.getFloat("hi"));
        }
        float from = t.getFloat("from"), to = t.getFloat("to");
        int time = Math.max(1, t.getInt("time"));
        return from + (to - from) * ease(Mth.clamp(e / time, 0.0F, 1.0F));
    }

    public float head(float partialTick) {
        return headAt(elapsed(partialTick));
    }

    /** How fast it's going along its way (blocks a tick, + towards its way's far end). */
    public float velocity(float partialTick) {
        float e = elapsed(partialTick);
        return headAt(e + 0.5F) - headAt(e - 0.5F);
    }

    public float speed(float partialTick) {
        return Math.abs(velocity(partialTick));
    }

    public boolean moving() {
        CompoundTag t = trip();
        float e = elapsed(0.0F);
        if (t.getInt("mode") == 1) {
            return speed(0.0F) > 1e-4F || (t.getFloat("vt") != 0.0F && e < t.getInt("delay") + 2);
        }
        return t.getFloat("from") != t.getFloat("to") && e < t.getInt("delay") + t.getInt("time");
    }

    /** How open its doors are (0 .. 1): open while it's stopped, shut before it sets off. */
    public float doors(float partialTick) {
        CompoundTag t = trip();
        if (t.getBoolean("pass")) {
            return 0.0F; // (stopping only to turn back: its doors stay shut)
        }
        float e = elapsed(partialTick), delay = t.getInt("delay"), dw = t.getFloat("dw");
        if (e < delay) {
            return dw * (1.0F - Mth.clamp(e / (SHUT - 4), 0.0F, 1.0F));
        }
        float stop;
        if (t.getInt("mode") == 1) {
            float v0 = t.getFloat("v0"), vt = t.getFloat("vt");
            if (vt != 0.0F) {
                return 0.0F;
            }
            if (v0 == 0.0F) {
                return Mth.clamp(dw + (e - delay) / 12.0F, 0.0F, 1.0F);
            }
            stop = delay + Math.abs(v0) / Math.max(t.getFloat("acc"), 1e-5F);
        } else {
            if (t.getFloat("from") == t.getFloat("to")) {
                return Mth.clamp(dw + (e - delay) / 12.0F, 0.0F, 1.0F);
            }
            stop = delay + t.getInt("time");
        }
        return Mth.clamp((e - stop - 6.0F) / 12.0F, 0.0F, 1.0F);
    }

    /** Whether its locomotive is at its way's far end (its front). */
    public boolean locoFront() {
        return !trip().contains("front") || trip().getBoolean("front");
    }

    public int locoUnit() {
        return locoFront() ? 0 : units() - 1;
    }

    /** What a unit is: 0 the locomotive, 1 a car, 2 a maglev's cab car (at its other end). */
    public int kind(int k) {
        if (k == locoUnit()) {
            return 0;
        }
        return style() == 0 && k == (locoFront() ? units() - 1 : 0) ? 2 : 1;
    }

    /**
     * Whether a unit's turned round (from the way its way runs). Every car keeps its own facing whichever way the train
     * runs (its locomotive's nose out ahead; a maglev's cab car's out behind; the cars between facing the locomotive) -
     * so turning the train back turns nothing in it (or anyone standing in it).
     */
    public boolean flipped(int k) {
        int kd = kind(k);
        if (kd == 0) {
            return k == units() - 1 && units() > 1;
        }
        if (kd == 2) {
            return k == units() - 1;
        }
        return !locoFront();
    }

    // ---- its units by where they are in it (0 its locomotive, on along it): the same whichever way it runs ----

    /** A unit's place along its way (0 its front), from its place in the train. */
    public int wayIndex(int p) {
        return locoFront() ? p : units() - 1 - p;
    }

    public Frame frameP(int p, float partialTick) {
        return frame(wayIndex(p), partialTick);
    }

    public Frame[] framesP(float partialTick) {
        Frame[] out = new Frame[units()];
        for (int p = 0; p < out.length; p++) {
            out[p] = frameP(p, partialTick);
        }
        return out;
    }

    public int kindP(int p) {
        return kind(wayIndex(p));
    }

    public boolean gangPlusP(int p) {
        return gangPlus(wayIndex(p));
    }

    public boolean gangMinusP(int p) {
        return gangMinus(wayIndex(p));
    }

    /** A unit's gangways, at its own +x end and its -x end (to the cars it joins). */
    public boolean gangPlus(int k) {
        return flipped(k) ? k < units() - 1 : k > 0;
    }

    public boolean gangMinus(int k) {
        return flipped(k) ? k > 0 : k < units() - 1;
    }

    /** A car in the world: its middle and its own axes (x its nose's way, y up, z across). */
    public record Frame(Vec3 mid, Vec3 f, Vec3 u, Vec3 s) {
        public Vec3 world(double x, double y, double z) {
            return mid.add(f.scale(x)).add(u.scale(y)).add(s.scale(z));
        }

        public Vec3 local(Vec3 w) {
            Vec3 d = w.subtract(mid);
            return new Vec3(d.dot(f), d.dot(u), d.dot(s));
        }

        public float yaw() {
            return (float) (Mth.atan2(f.z, f.x) * Mth.RAD_TO_DEG);
        }
    }

    /** How far a unit leans into the curve it's on (radians, towards its own +z), now: more the faster and tighter. */
    public float bank(int k, float partialTick) {
        float c = head(partialTick) - k * CAR - BODY / 2.0F;
        Vec3 fa = at(c + BODY * 0.4F + 0.6F).subtract(at(c + BODY * 0.4F - 0.6F)), ba = at(c - BODY * 0.4F + 0.6F).subtract(at(c - BODY * 0.4F - 0.6F));
        if (fa.lengthSqr() < 1e-6 || ba.lengthSqr() < 1e-6) {
            return 0.0F;
        }
        fa = new Vec3(fa.x, 0, fa.z).normalize();
        ba = new Vec3(ba.x, 0, ba.z).normalize();
        double crossY = ba.z * fa.x - ba.x * fa.z;
        float v = speed(partialTick);
        float b = Mth.clamp((float) (-crossY / (BODY * 0.8F)) * v * v * 40.0F, -0.14F, 0.14F);
        return flipped(k) ? -b : b;
    }

    /** Unit k's place, now: 0 at the front (its way's far end). */
    public Frame frame(int k, float partialTick) {
        float c = head(partialTick) - k * CAR - BODY / 2.0F;
        Vec3 a = at(c + BODY * 0.4F), b = at(c - BODY * 0.4F);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 dir = a.subtract(b);
        dir = dir.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : dir.normalize();
        if (flipped(k)) {
            dir = dir.scale(-1);
        }
        Vec3 s = dir.cross(new Vec3(0, 1, 0));
        s = s.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : s.normalize();
        Vec3 u = s.cross(dir);
        float r = bank(k, partialTick);
        if (r != 0.0F) {
            double cr = Math.cos(r), sr = Math.sin(r);
            Vec3 u2 = u.scale(cr).add(s.scale(sr)), s2 = s.scale(cr).subtract(u.scale(sr));
            u = u2;
            s = s2;
        }
        return new Frame(mid, dir, u, s);
    }

    public Frame[] frames(float partialTick) {
        Frame[] out = new Frame[units()];
        for (int k = 0; k < out.length; k++) {
            out[k] = frame(k, partialTick);
        }
        return out;
    }

    /** Its cars (by their places in it) as walls and floors see them: on a client, as at the end of the last tick. */
    public Frame[] solidFrames() {
        Frame[] fr = clientFrames;
        return level().isClientSide && fr != null && fr.length == units() ? fr : framesP(0.0F);
    }

    /** Unit k's middle and the way along its way it points (towards the way's far end). */
    public Vec3[] unit(int k, float partialTick) {
        Frame f = frame(k, partialTick);
        return new Vec3[] {f.mid(), flipped(k) ? f.f().scale(-1) : f.f()};
    }

    public Vec3 end() {
        return driven() ? at(head(0.0F)) : at(trip().getFloat("to"));
    }

    private void setTrip(CompoundTag t, List<Vec3> way) {
        int[] bits = new int[way.size() * 3];
        for (int i = 0; i < way.size(); i++) {
            bits[i * 3] = Float.floatToIntBits((float) way.get(i).x);
            bits[i * 3 + 1] = Float.floatToIntBits((float) way.get(i).y);
            bits[i * 3 + 2] = Float.floatToIntBits((float) way.get(i).z);
        }
        t.putIntArray("pts", bits);
        t.putLong("start", level().getGameTime());
        entityData.set(TRIP, t);
    }

    private static float lengthOf(List<Vec3> way) {
        float d = 0.0F;
        for (int i = 1; i < way.size(); i++) {
            d += (float) way.get(i).distanceTo(way.get(i - 1));
        }
        return d;
    }

    /** Standing still on this way, its front this far along it. */
    private void stand(List<Vec3> way, float at, boolean front, float doorsWere) {
        CompoundTag t = new CompoundTag();
        t.putInt("mode", 0);
        t.putFloat("from", at);
        t.putFloat("to", at);
        t.putInt("time", 1);
        t.putBoolean("front", front);
        t.putFloat("dw", doorsWere);
        setTrip(t, way);
    }

    /** Put on a line of rails: its front at a rail, facing the way given, its cars along the line behind. */
    public void placeAt(BlockPos rail, Vec3 facing) {
        placeAt(TrainLine.centre(level(), rail), facing);
    }

    /** Put on a line (rails or track): its front here, facing the way given (the way you looked), its cars along it behind. */
    public void placeAt(Vec3 at, Vec3 facing) {
        Vec3 f = new Vec3(facing.x, 0, facing.z);
        f = f.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : f.normalize();
        List<Vec3> behind = new ArrayList<>(TrainRoute.onward(level(), at, f.scale(-1), 160).pts());
        Collections.reverse(behind);
        List<Vec3> way = new ArrayList<>(behind);
        way.add(at);
        if (way.size() == 1) {
            way.add(0, at.subtract(f));
        }
        way = clean(way);
        float s = lengthOf(way);
        way.addAll(TrainRoute.onward(level(), at, f, 6).pts());
        stand(clean(way), s, true, 1.0F);
        Vec3 h = at(s);
        setPos(h.x, h.y, h.z);
    }

    private static final float FAR = 2048.0F, REACH_D = 160.0F;

    /** The way it's going at its front (along its way), and at its back (outward). */
    private Vec3 frontWay(float h) {
        Vec3 d = at(h).subtract(at(h - 1.0F));
        return d.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : d.normalize();
    }

    private Vec3 backWay(float h) {
        Vec3 d = at(h - length()).subtract(at(h - length() + 1.0F));
        return d.lengthSqr() < 1e-6 ? new Vec3(-1, 0, 0) : d.normalize();
    }

    /** The way on past its front, and past its back. */
    private TrainRoute.Onward ahead(float h, float most) {
        return TrainRoute.onward(level(), at(h), frontWay(h), most);
    }

    private TrainRoute.Onward astern(float h, float most) {
        return TrainRoute.onward(level(), at(h - length()), backWay(h), most);
    }

    /** The points of its way under it now (from just behind its back to its front). */
    private List<Vec3> under(float h) {
        List<Vec3> way = new ArrayList<>();
        float tail = h - length() - 0.5F;
        parse();
        way.add(at(tail));
        for (int i = 0; i < cum.length; i++) {
            if (cum[i] > tail && cum[i] < h) {
                way.add(new Vec3(pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2]));
            }
        }
        way.add(at(h));
        return way;
    }

    private static List<Vec3> clean(List<Vec3> way) {
        List<Vec3> out = new ArrayList<>();
        for (Vec3 v : way) {
            if (out.isEmpty() || out.get(out.size() - 1).distanceToSqr(v) > 1e-4) {
                out.add(v);
            }
        }
        return out;
    }

    /** The index of the point on a way nearest a spot (within reach), or -1; and how far along the way it is. */
    private static int find(List<Vec3> pts, Vec3 p, double reach) {
        int best = -1;
        double bd = reach * reach;
        for (int i = 0; i < pts.size(); i++) {
            double d = pts.get(i).distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private static float distTo(TrainRoute.Branch b, Vec3 p) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < b.pts().size(); i++) {
            if (b.pts().get(i).distanceToSqr(p) < 1.0) {
                best = Math.min(best, b.dist().get(i));
            }
        }
        return best;
    }

    private static float along(Vec3 from, List<Vec3> pts, int upTo) {
        float d = 0.0F;
        Vec3 last = from;
        for (int i = 0; i <= upTo; i++) {
            d += (float) pts.get(i).distanceTo(last);
            last = pts.get(i);
        }
        return d;
    }

    /**
     * Sent to a station on its line: there from its front if it's ahead, or backing up to it if it's behind (onlyAhead:
     * never back). Its doors shut first. Returns whether it's going (or there already).
     */
    public boolean goTo(BlockPos station, boolean onlyAhead) {
        Vec3 stop = TrainRoute.stopPoint(level(), station);
        float h = head(0.0F);
        if (stop == null || !TrainRoute.onLine(level(), at(h))) {
            return false;
        }
        if (!moving() && (at(h).distanceTo(stop) < 1.0 || inside(stop.add(0, 1.5, 0)) >= 0) && !onlyAhead) {
            return true;
        }
        // smooth track: planned - its switches set as it goes, backing up past one if it must
        if (TrackNetwork.of(level()).nearest(at(h), 0.8) != null) {
            List<TrainRoute.Leg> legs = TrainRoute.plan(level(), at(h), frontWay(h), at(h - length()), backWay(h), length(), stop, true);
            if (legs == null || legs.isEmpty()) {
                return false;
            }
            plan = legs;
            planLeg = 0;
            startLeg();
            return true;
        }
        List<Vec3> ahead = ahead(h, FAR).pts();
        List<Vec3> astern = onlyAhead ? List.of() : astern(h, FAR).pts();
        int ia = find(ahead, stop, 1.0), ib = find(astern, stop, 1.0);
        if (ia < 0 && ib < 0) {
            // not the way the switches are set: the way they could be, set for it (the nearer way, ahead or back)
            TrainRoute.Branch ra = TrainRoute.routeTo(level(), at(h), frontWay(h), stop, FAR);
            TrainRoute.Branch rb = onlyAhead ? null : TrainRoute.routeTo(level(), at(h - length()), backWay(h), stop, FAR);
            float da = ra == null ? Float.MAX_VALUE : distTo(ra, stop), db = rb == null ? Float.MAX_VALUE : distTo(rb, stop);
            TrainRoute.Branch r = da <= db ? ra : rb;
            if (r == null) {
                return false;
            }
            TrainRoute.setSwitches(level(), r.switches());
            ahead = ahead(h, FAR).pts();
            astern = onlyAhead ? List.of() : astern(h, FAR).pts();
            ia = find(ahead, stop, 1.0);
            ib = find(astern, stop, 1.0);
            if (ia < 0 && ib < 0) {
                return false;
            }
        }
        float da = ia < 0 ? Float.MAX_VALUE : along(at(h), ahead, ia), db = ib < 0 ? Float.MAX_VALUE : along(at(h - length()), astern, ib);
        boolean forward = da <= db;
        List<Vec3> under = under(h);
        List<Vec3> way = new ArrayList<>();
        float from;
        boolean loco = locoFront();
        if (forward) {
            way.addAll(under);
            from = lengthOf(clean(way));
            way.addAll(ahead.subList(0, ia + 1));
        } else {
            Collections.reverse(under);
            way.addAll(under);
            from = lengthOf(clean(way));
            way.addAll(astern.subList(0, ib + 1));
            loco = !loco;
        }
        way = clean(way);
        float doorsNow = doors(0.0F);
        CompoundTag t = new CompoundTag();
        t.putInt("mode", 0);
        t.putFloat("from", from);
        t.putFloat("to", lengthOf(way));
        t.putInt("time", Math.max(20, Math.round(Math.abs(lengthOf(way) - from) / top() * 1.5F)));
        t.putInt("delay", doorsNow > 0.01F ? SHUT : 0);
        t.putFloat("dw", doorsNow);
        t.putBoolean("front", loco);
        setTrip(t, way);
        entityData.set(NOTCH, 0);
        arrivedAt = Long.MIN_VALUE;
        level().playSound(null, getX(), getY(), getZ(), style() == 1 ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL,
                0.8F, style() == 1 ? 0.6F : 1.6F);
        return true;
    }

    public boolean goTo(BlockPos station) {
        return goTo(station, false);
    }

    /** (server) Its planned way: its legs, which it's on, when it stopped between them. */
    @Nullable
    private List<TrainRoute.Leg> plan;
    private int planLeg;
    private long legDone = Long.MIN_VALUE;

    /** Sets out on the leg of its plan it's on: its switches set; turned back, if it starts so; its doors kept shut till the last. */
    private void startLeg() {
        TrainRoute.Leg leg = plan.get(planLeg);
        boolean last = planLeg == plan.size() - 1;
        TrainRoute.setSwitches(level(), leg.switches());
        float h = head(0.0F);
        List<Vec3> under = under(h);
        boolean loco = locoFront();
        Vec3 from0, dir;
        if (leg.reverse()) {
            Collections.reverse(under);
            loco = !loco;
            from0 = at(h - length());
            dir = backWay(h);
        } else {
            from0 = at(h);
            dir = frontWay(h);
        }
        List<Vec3> way = new ArrayList<>(under);
        way = clean(way);
        float from = lengthOf(way);
        boolean on = false;
        for (Vec3 p : leg.pts()) {
            if (!on && p.subtract(from0).dot(dir) > 0.02) {
                on = true;
            }
            if (on) {
                way.add(p);
            }
        }
        way = clean(way);
        if (lengthOf(way) - from < 0.05) {
            // (nothing to go on this leg: the next, or done)
            planLeg++;
            if (planLeg < plan.size()) {
                startLeg();
            } else {
                plan = null;
            }
            return;
        }
        float doorsNow = doors(0.0F);
        CompoundTag t = new CompoundTag();
        t.putInt("mode", 0);
        t.putFloat("from", from);
        t.putFloat("to", Math.max(from, lengthOf(way)));
        t.putInt("time", Math.max(20, Math.round(Math.abs(lengthOf(way) - from) / top() * 1.5F)));
        t.putInt("delay", doorsNow > 0.01F ? SHUT : 0);
        t.putFloat("dw", doorsNow);
        t.putBoolean("front", loco);
        t.putBoolean("pass", !last);
        setTrip(t, way);
        entityData.set(NOTCH, 0);
        arrivedAt = Long.MIN_VALUE;
        if (planLeg == 0) {
            level().playSound(null, getX(), getY(), getZ(), style() == 1 ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL,
                    0.8F, style() == 1 ? 0.6F : 1.6F);
        }
    }

    // ---- driving ----

    /**
     * Set out (or on) towards a speed, from where it is: its way rebuilt round it - the line on ahead and behind, as far
     * as it knows - and where it must stop (the line's ends).
     */
    private void drive(float vt, float acc) {
        float h = head(0.0F), v = velocity(0.0F), doorsNow = doors(0.0F);
        boolean still = Math.abs(v) < 1e-4F;
        TrainRoute.Onward ahead = ahead(h, REACH_D), astern = astern(h, REACH_D);
        List<Vec3> way = new ArrayList<>(astern.pts());
        Collections.reverse(way);
        way.addAll(under(h));
        way = clean(way);
        float s = lengthOf(way);
        way.addAll(ahead.pts());
        way = clean(way);
        CompoundTag t = new CompoundTag();
        t.putInt("mode", 1);
        t.putFloat("s0", s);
        t.putFloat("v0", still ? 0.0F : v);
        t.putFloat("vt", vt);
        t.putFloat("acc", acc);
        t.putInt("delay", still && vt != 0.0F && doorsNow > 0.01F ? SHUT : 0);
        t.putFloat("dw", still ? doorsNow : 0.0F);
        t.putFloat("lo", length() + 0.3F);
        t.putFloat("hi", lengthOf(way) - 0.3F);
        t.putBoolean("openLo", astern.open() || astern.looped());
        t.putBoolean("openHi", ahead.open() || ahead.looped());
        t.putBoolean("front", locoFront());
        setTrip(t, way);
        arrivedAt = Long.MIN_VALUE;
    }

    /** Its throttle set from a cab (that unit): -3 .. 6 notches its nose's way; brake: stop, hard. */
    public void throttle(int cab, int notch, boolean brake) {
        if (cab < 0 || cab >= units() || kindP(cab) == 1) {
            return;
        }
        int unit = wayIndex(cab);
        if (loop()) {
            entityData.set(LOOP, false);
        }
        plan = null;
        notch = Mth.clamp(notch, NOTCH_MIN, NOTCH_MAX);
        entityData.set(NOTCH, brake ? 0 : notch);
        float sign = flipped(unit) ? -1.0F : 1.0F;
        float vt = brake ? 0.0F : sign * top() * notch / NOTCH_MAX;
        if (Math.abs(velocity(0.0F)) < 1e-4F && vt == 0.0F && driven() && trip().getFloat("vt") == 0.0F) {
            return; // (already standing)
        }
        drive(vt, brake ? BRAKE : ACC[style()]);
    }

    /** (server, each tick, driven) Stops it before the line's end; knows its way further on as it goes. */
    private void keepOnLine() {
        CompoundTag t = trip();
        float v = velocity(0.0F), h = head(0.0F), vt = t.getFloat("vt");
        float dir = Math.abs(v) > 1e-4F ? Math.signum(v) : Math.signum(vt);
        if (dir == 0.0F) {
            return;
        }
        float room = dir > 0 ? t.getFloat("hi") - h : h - t.getFloat("lo");
        boolean open = dir > 0 ? t.getBoolean("openHi") : t.getBoolean("openLo");
        if (open && room < 80.0F && tickCount % 10 == 0) {
            drive(vt, t.getFloat("acc"));
            return;
        }
        if (!open && vt != 0.0F) {
            float a = ACC[style()];
            if (room < v * v / (2.0F * a) + Math.abs(v) * 2.0F + 0.5F) {
                drive(0.0F, Math.max(a, v * v / (2.0F * Math.max(room - 0.3F, 0.2F))));
                entityData.set(NOTCH, 0);
            }
        }
    }

    // ---- stations ----

    /** The stations along its line: [ahead of its front, behind its back] - nearest first. */
    public List<List<BlockPos>> stations() {
        List<List<BlockPos>> out = new ArrayList<>();
        float h = head(0.0F);
        if (!TrainRoute.onLine(level(), at(h))) {
            return out;
        }
        // (any way the switches could send it)
        List<BlockPos> ahead = new ArrayList<>();
        // (any it's standing by first)
        for (BlockPos st : TrainRoute.allStations(level())) {
            Vec3 stop = TrainRoute.stopPoint(level(), st);
            if (stop != null && (inside(stop.add(0, 1.5, 0)) >= 0 || at(h).distanceToSqr(stop) < 2.5)) {
                ahead.add(st);
            }
        }
        for (BlockPos st : TrainRoute.stationsAnyWay(level(), at(h), frontWay(h), FAR)) {
            if (!ahead.contains(st)) {
                ahead.add(st);
            }
        }
        out.add(ahead);
        out.add(TrainRoute.stationsAnyWay(level(), at(h - length()), backWay(h), FAR));
        return out;
    }

    // ---- each tick ----

    @Override
    public void tick() {
        super.tick();
        Frame lf = frame(locoUnit(), 0.0F);
        setPos(lf.mid().x, lf.mid().y, lf.mid().z);
        if (level().isClientSide) {
            if (style() == 1 && random.nextFloat() < 0.25F + speed(0.0F) * 1.5F) {
                Vec3 c = lf.world(HL - 0.6, 5.0, 0.0), n = lf.f();
                level().addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y, c.z, -n.x * speed(0.0F) * 0.5,
                        0.06, -n.z * speed(0.0F) * 0.5);
            }
            return;
        }
        if (driven()) {
            keepOnLine();
        }
        carry();
        boolean moving = moving();
        if (wasMoving && !moving) {
            if (plan != null && planLeg < plan.size() - 1) {
                legDone = level().getGameTime();
            } else {
                plan = null;
                arrivedAt = level().getGameTime();
                level().playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1.0F, 1.2F);
            }
        }
        wasMoving = moving;
        // (between legs of its plan: a moment, then on - turned back)
        if (plan != null && !moving && legDone != Long.MIN_VALUE && level().getGameTime() - legDone >= 12) {
            legDone = Long.MIN_VALUE;
            planLeg++;
            if (planLeg < plan.size()) {
                startLeg();
            } else {
                plan = null;
            }
        }
        // players standing in it: not falling, not flying
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBoxForCulling())) {
            if (!p.isPassenger() && inside(p.position()) >= 0) {
                p.fallDistance = 0.0F;
                if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
                    ElevatorCabinEntity.notFlying(sp);
                }
            }
        }
        if (loop() && plan == null && !moving && arrivedAt != Long.MIN_VALUE && level().getGameTime() - arrivedAt >= WAIT) {
            arrivedAt = level().getGameTime();
            goRound();
        }
        if (arrivedAt == Long.MIN_VALUE && !moving && loop()) {
            arrivedAt = level().getGameTime();
        }
    }

    /** Going round: on to the next station in its order (worked out, if it hasn't one: see defaultOrder). */
    private void goRound() {
        float h = head(0.0F);
        if (!TrainRoute.onLine(level(), at(h))) {
            return;
        }
        List<BlockPos> order = order();
        if (order.isEmpty() && !customOrder()) {
            setOrder(defaultOrder(), false);
            order = order();
        }
        for (int tries = 0; tries < order.size(); tries++) {
            BlockPos s = order.get(Math.floorMod(nextStop, order.size()));
            nextStop = Math.floorMod(nextStop + 1, order.size());
            Vec3 stop = TrainRoute.stopPoint(level(), s);
            if (stop == null || (stop.distanceTo(at(h)) < 2.0 || inside(stop.add(0, 1.5, 0)) >= 0)) {
                continue; // (there now, or gone)
            }
            if (goTo(s, false)) {
                return;
            }
        }
    }

    /** Whether a point (in a unit's own space) is in it: its floor to its ceiling, between its walls, its gangways too. */
    public static boolean within(Vec3 l) {
        return Math.abs(l.x) < HL + GANG_OUT && Math.abs(l.z) < OW && l.y > FL - 0.6 && l.y < CE;
    }

    /** Which unit a point's inside, or -1. */
    public int inside(Vec3 p) {
        Frame[] fr = solidFrames();
        for (int k = 0; k < fr.length; k++) {
            if (within(fr[k].local(p))) {
                return k;
            }
        }
        return -1;
    }

    /** (server) Mobs and dropped things standing in it ride along with it. */
    private void carry() {
        Frame[] now = framesP(0.0F);
        Frame[] was = lastFrames;
        lastFrames = now;
        if (was == null || was.length != now.length) {
            return;
        }
        for (Entity e : level().getEntities(this, getBoundingBoxForCulling(),
                e -> (e instanceof LivingEntity || e instanceof ItemEntity) && !(e instanceof Player) && !e.isPassenger())) {
            for (int k = 0; k < now.length; k++) {
                Vec3 l = was[k].local(e.position());
                if (within(l)) {
                    Vec3 p = now[k].world(l.x, l.y, l.z);
                    e.setPos(p.x, p.y, p.z);
                    e.setYRot(e.getYRot() + Mth.wrapDegrees(now[k].yaw() - was[k].yaw()));
                    break;
                }
            }
        }
    }

    // ---- its walls (TrainCollision; drawn to match by client/TrainRenderer's shader) ----

    private static void box(List<float[]> out, double x0, double y0, double z0, double x1, double y1, double z1) {
        out.add(new float[] {(float) Math.min(x0, x1), (float) Math.min(y0, y1), (float) Math.min(z0, z1), (float) Math.max(x0, x1),
                (float) Math.max(y0, y1), (float) Math.max(z0, z1)});
    }

    /** Its side walls (both sides), with door gaps. */
    private static void sides(List<float[]> out, float x0, float x1, float[] gaps) {
        for (int sg = -1; sg <= 1; sg += 2) {
            float a = x0;
            for (float g : gaps) {
                box(out, a, FL, sg * IW, g - DOOR_HALF, CE, sg * OW);
                box(out, g - DOOR_HALF, FL + DOOR_H, sg * IW, g + DOOR_HALF, CE, sg * OW);
                a = g + DOOR_HALF;
            }
            box(out, a, FL, sg * IW, x1, CE, sg * OW);
        }
    }

    /** An end wall, its outside at x (out: which way's outward, +1 or -1), with a gangway through it and out, or shut. */
    private static void endWall(List<float[]> out, float x, int outward, boolean gang) {
        float in = x - outward * 0.15F;
        if (!gang) {
            box(out, in, FL, -OW, x, CE, OW);
            return;
        }
        box(out, in, FL, -OW, x, CE, -GANG_HALF);
        box(out, in, FL, GANG_HALF, x, CE, OW);
        box(out, in, FL + GANG_H, -GANG_HALF, x, CE, GANG_HALF);
        float o = x + outward * GANG_OUT;
        box(out, x, FL - 0.2, -GANG_HALF, o, FL, GANG_HALF);
        box(out, x, FL, GANG_HALF, o, FL + GANG_H, GANG_HALF + 0.15);
        box(out, x, FL, -GANG_HALF - 0.15, o, FL + GANG_H, -GANG_HALF);
        box(out, x, FL + GANG_H, -GANG_HALF - 0.15, o, FL + GANG_H + 0.15, GANG_HALF + 0.15);
    }

    /** A unit's seats: x along it, z across. In a cab, the first is the driver's. */
    public static float[][] seatsOf(int style, int kind) {
        if (kind == 0 && style == 1) {
            return new float[][] {{-3.0F, 0.6F}};
        }
        float[] xs = kind == 1 ? new float[] {-5.1F, -1.8F, -0.6F, 0.6F, 1.8F, 5.1F} : new float[] {-5.1F, -1.8F, -0.6F, 0.6F, 1.8F};
        List<float[]> out = new ArrayList<>();
        if (kind != 1) {
            out.add(new float[] {4.0F, 0.0F});
        }
        for (float x : xs) {
            out.add(new float[] {x, -1.0F});
            out.add(new float[] {x, 1.0F});
        }
        return out.toArray(new float[0][]);
    }

    /** Which way a seat faces along its car: +1 its nose's way, -1 back (as the shader draws their backs). */
    public static int seatFacing(int style, int kind, int seat) {
        if (kind == 0 && style == 1) {
            return 1;
        }
        if (kind != 1) {
            if (seat == 0) {
                return 1;
            }
            seat--;
        }
        int xi = seat / 2;
        return xi == 2 || xi == 4 || xi == 5 ? -1 : 1;
    }

    public static boolean driverSeat(int kind, int seat) {
        return kind != 1 && seat == 0;
    }

    /** A unit's solid parts, in its own space: [x0 y0 z0 x1 y1 z1] each. */
    public static List<float[]> boxes(int style, int kind, boolean plus, boolean minus, float doors) {
        List<float[]> out = new ArrayList<>();
        if (style == 1 && kind == 0) {
            // a steam locomotive: its cab behind (its sides open), its boiler and all in front
            float cab = -1.2F;
            box(out, -HL, FL - 0.35, -OW, cab, FL, OW);
            box(out, -HL - 0.1, CE, -OW - 0.1, cab, CE + 0.2, OW + 0.1);
            for (int sg = -1; sg <= 1; sg += 2) {
                box(out, -HL, FL, sg * IW, -4.6, CE, sg * OW);
                box(out, -2.2, FL, sg * IW, cab, CE, sg * OW);
            }
            box(out, cab - 0.15, FL, -OW, cab, CE, OW);
            endWall(out, -HL, -1, minus);
            box(out, cab, 0.3, -1.3, HL + 0.4, 4.0, 1.3);
            box(out, -3.35, FL, 0.25, -2.65, FL + 0.45, 0.95);
            return out;
        }
        boolean open = doors > 0.85F;
        box(out, -HL, FL - 0.35, -OW, HL, FL, OW);
        box(out, -HL, CE, -OW, HL, CE + 0.25, OW);
        sides(out, -HL, HL, !open ? new float[0] : kind == 1 ? new float[] {-DOOR_X, DOOR_X} : new float[] {-DOOR_X});
        endWall(out, -HL, -1, minus);
        if (kind == 1) {
            endWall(out, HL, 1, plus);
        } else {
            // a cab: its bulkhead (a doorway through), its desk, its nose
            box(out, 2.6, FL, -IW, 2.75, CE, -0.5);
            box(out, 2.6, FL, 0.5, 2.75, CE, IW);
            box(out, 2.6, FL + 2.2, -0.5, 2.75, CE, 0.5);
            box(out, 4.9, FL, -1.2, 5.6, FL + 1.0, 1.2);
            box(out, HL - 0.15, FL, -OW, HL + 0.9, CE - 0.6, OW);
        }
        float[][] ss = seatsOf(style, kind);
        for (int i = 0; i < ss.length; i++) {
            float w = driverSeat(kind, i) ? 0.35F : 0.45F;
            box(out, ss[i][0] - 0.4, FL, ss[i][1] - w, ss[i][0] + 0.4, FL + 0.45, ss[i][1] + w);
        }
        return out;
    }

    // ---- riders ----

    private void syncSeats() {
        int[] ids = new int[seats.size()], keys = new int[seats.size()];
        int i = 0;
        for (Map.Entry<Integer, Integer> e : seats.entrySet()) {
            ids[i] = e.getKey();
            keys[i++] = e.getValue();
        }
        CompoundTag t = new CompoundTag();
        t.putIntArray("ids", ids);
        t.putIntArray("keys", keys);
        entityData.set(SEATS, t);
    }

    /** Where a rider sits (unit * 16 + seat). */
    @Nullable
    public Integer seatOf(Entity rider) {
        if (!level().isClientSide) {
            return seats.get(rider.getId());
        }
        CompoundTag t = entityData.get(SEATS);
        int[] ids = t.getIntArray("ids"), keys = t.getIntArray("keys");
        for (int i = 0; i < Math.min(ids.length, keys.length); i++) {
            if (ids[i] == rider.getId()) {
                return keys[i];
            }
        }
        return null;
    }

    /** Sits a rider in a seat (key: unit * 16 + seat; taken: the next free one in that unit). */
    public boolean sit(Entity rider, int key) {
        int unit = key / 16, want = key % 16;
        if (unit < 0 || unit >= units() || rider.isPassenger()) {
            return false;
        }
        int n = seatsOf(style(), kindP(unit)).length;
        List<Integer> order = new ArrayList<>();
        order.add(Math.min(want, n - 1));
        for (int s = 0; s < n; s++) {
            if (!order.contains(s)) {
                order.add(s);
            }
        }
        for (int s : order) {
            int k = unit * 16 + s;
            if (!seats.containsValue(k)) {
                seats.put(rider.getId(), k);
                if (rider.startRiding(this, true)) {
                    syncSeats();
                    return true;
                }
                seats.remove(rider.getId());
                return false;
            }
        }
        if (rider instanceof Player p) {
            p.displayClientMessage(Component.translatable("message.skyblocks.train.full"), true);
        }
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < units() * 12;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (seats.remove(passenger.getId()) != null && !level().isClientSide) {
            syncSeats();
        }
    }

    private float[] seatSpot(int key) {
        int unit = Math.min(key / 16, units() - 1);
        float[][] all = seatsOf(style(), kindP(unit));
        return all[Math.min(key % 16, all.length - 1)];
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        Integer key = seatOf(passenger);
        if (key == null) {
            key = 0;
        }
        int unit = Math.min(key / 16, units() - 1);
        float[] s = seatSpot(key);
        Vec3 p = solidFrames()[unit].world(s[0], FL - 0.2, s[1]);
        move.accept(passenger, p.x, p.y, p.z);
        passenger.fallDistance = 0.0F;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Integer key = seatOf(passenger);
        int unit = key == null ? 0 : Math.min(key / 16, units() - 1);
        float[] s = key == null ? new float[] {0, 0} : seatSpot(key);
        return frameP(unit, 0.0F).world(s[0], FL + 0.05, 0.0);
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    // ---- as an entity ----

    @Override
    protected AABB makeBoundingBox() {
        return new AABB(getX() - 1.0, getY(), getZ() - 1.0, getX() + 1.0, getY() + 2.6, getZ() + 1.0);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(length() + 3.0, 6.0, length() + 3.0);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    /** Taken up again (from its screen): its riders let off, it gone, its item given back. */
    public void pickUp(Player player) {
        ejectPassengers();
        ItemStack item = new ItemStack(ModItems.TRAIN.get());
        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
        discard();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 192.0 * 192.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setStyle(tag.getInt("style"));
        setCars(tag.contains("cars") ? tag.getInt("cars") : 2);
        entityData.set(LOOP, tag.getBoolean("loop"));
        entityData.set(SKIP, tag.getCompound("skip"));
        entityData.set(ORDER, tag.getCompound("order"));
        nextStop = tag.getInt("next");
        CompoundTag t = tag.getCompound("trip");
        // (saved mid-trip: standing where it was, its doors open)
        int[] bits = t.getIntArray("pts");
        List<Vec3> way = new ArrayList<>();
        for (int i = 0; i + 2 < bits.length; i += 3) {
            way.add(new Vec3(Float.intBitsToFloat(bits[i]), Float.intBitsToFloat(bits[i + 1]), Float.intBitsToFloat(bits[i + 2])));
        }
        float at = tag.contains("head") ? tag.getFloat("head") : t.getFloat("to");
        if (way.size() >= 2) {
            stand(way, at, !t.contains("front") || t.getBoolean("front"), 1.0F);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("style", style());
        tag.putInt("cars", cars());
        tag.putBoolean("loop", loop());
        tag.put("trip", trip().copy());
        tag.putFloat("head", head(0.0F));
        tag.put("skip", entityData.get(SKIP).copy());
        tag.put("order", entityData.get(ORDER).copy());
        tag.putInt("next", nextStop);
    }
}
