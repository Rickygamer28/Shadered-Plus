package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A train: a locomotive and its cars (1 .. 4), running along a line of train rails (TrainLine) between stations. Where
 * it is is worked out from its trip - the way along the rails (points), how far along it it set off from and is going to,
 * when it set off, how long it takes - known alike to the server and every client, so it glides smoothly; each car
 * follows the way at its own distance behind, turned along it (round corners, up slopes). Sent to a station it goes
 * there - backing up, locomotive last, if the station's behind it. Riders sit in its seats (StationBlock calls it;
 * client/TrainScreen sends it; client/TrainRenderer draws it).
 */
public class TrainEntity extends Entity {
    /** Each car's length and the gap after it (blocks), its body's length; its looks. */
    public static final float CAR = 5.4F, BODY = 5.0F;
    public static final String[] STYLES = {"maglev", "steam"};
    /** Its top speeds (blocks a tick), by look; how long it waits at a station going round (ticks). */
    private static final float[] SPEED = {0.6F, 0.4F};
    private static final int WAIT = 100;

    private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CARS = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LOOP = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    /** Its trip: pts (the way: x y z ..., as float bits), from, to (how far along it), start (game time), time (ticks), front (the locomotive leads). */
    private static final EntityDataAccessor<CompoundTag> TRIP = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** Who sits where (ids, keys: car * 8 + seat), for the clients. */
    private static final EntityDataAccessor<CompoundTag> SEATS = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.COMPOUND_TAG);

    /** The way, as last read from its trip: its points and how far along it each is. */
    private CompoundTag parsedFrom;
    private float[] pts = new float[0];
    private float[] cum = new float[0];
    /** Who sits where: rider id -> car * 8 + seat. */
    private final Map<Integer, Integer> seats = new HashMap<>();
    /** (server) When it got where it was going, for going round. */
    private long arrivedAt = Long.MIN_VALUE;
    private boolean wasMoving;

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
        builder.define(TRIP, new CompoundTag());
        builder.define(SEATS, new CompoundTag());
    }

    // ---- what it is ----

    public int style() {
        return entityData.get(STYLE);
    }

    public int cars() {
        return entityData.get(CARS);
    }

    /** Its locomotive and cars. */
    public int units() {
        return cars() + 1;
    }

    public boolean loop() {
        return entityData.get(LOOP);
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
    }

    /** Its whole length (blocks). */
    public float length() {
        return units() * CAR;
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

    /** How far along the way the whole of it goes. */
    public float total() {
        parse();
        return cum.length == 0 ? 0.0F : cum[cum.length - 1];
    }

    /** A place on the way, this far along it (held to its ends). */
    public Vec3 at(float s) {
        parse();
        int n = cum.length;
        if (n == 0) {
            return position();
        }
        if (s <= 0 || n == 1) {
            return new Vec3(pts[0], pts[1], pts[2]);
        }
        if (s >= cum[n - 1]) {
            return new Vec3(pts[n * 3 - 3], pts[n * 3 - 2], pts[n * 3 - 1]);
        }
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (cum[mid] <= s) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        float f = (s - cum[lo]) / Math.max(cum[hi] - cum[lo], 1e-6F);
        return new Vec3(Mth.lerp(f, pts[lo * 3], pts[hi * 3]), Mth.lerp(f, pts[lo * 3 + 1], pts[hi * 3 + 1]), Mth.lerp(f, pts[lo * 3 + 2], pts[hi * 3 + 2]));
    }

    /** (client) The trip it last saw start, and its own tick count then less how long it had been going (as the elevator's cabin). */
    private long seenStart = Long.MIN_VALUE;
    private int tripFrom;

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

    /** How far along the way its front is, now. */
    public float head(float partialTick) {
        CompoundTag t = trip();
        float from = t.getFloat("from"), to = t.getFloat("to");
        int time = Math.max(1, t.getInt("time"));
        return from + (to - from) * ease(Mth.clamp(elapsed(partialTick) / time, 0.0F, 1.0F));
    }

    public boolean moving() {
        return elapsed(0.0F) < trip().getInt("time") && trip().getFloat("from") != trip().getFloat("to");
    }

    /** How fast it's going (blocks a tick). */
    public float speed(float partialTick) {
        return Math.abs(head(partialTick + 0.5F) - head(partialTick - 0.5F));
    }

    /** Where it's going (its trip's end). */
    public Vec3 end() {
        return at(trip().getFloat("to"));
    }

    /** The way its locomotive's nose points, now. */
    public Vec3 noseDir(float partialTick) {
        Vec3 d = unit(locoUnit(), partialTick)[1];
        return locoFront() ? d : d.scale(-1);
    }

    /** Whether its locomotive is in front (it's not backing up). */
    public boolean locoFront() {
        return !trip().contains("front") || trip().getBoolean("front");
    }

    /**
     * Where each of its units is, now: its middle, and the way it faces (its length along the rails between its front
     * and back wheels). Unit 0 is at the front; the locomotive is unit 0, or - backing up - the last.
     */
    public Vec3[] unit(int k, float partialTick) {
        float c = head(partialTick) - k * CAR - BODY / 2.0F;
        Vec3 f = at(c + BODY * 0.4F), b = at(c - BODY * 0.4F);
        Vec3 mid = f.add(b).scale(0.5);
        Vec3 dir = f.subtract(b);
        if (dir.lengthSqr() < 1e-6) {
            dir = new Vec3(1, 0, 0);
        }
        return new Vec3[] {mid, dir.normalize()};
    }

    /** Which unit is its locomotive. */
    public int locoUnit() {
        return locoFront() ? 0 : units() - 1;
    }

    /** Sets out (or sets it where it is, standing): this way, from this far along it to that far, its locomotive leading or not. */
    private void setTrip(List<Vec3> way, float from, float to, boolean front) {
        int[] bits = new int[way.size() * 3];
        for (int i = 0; i < way.size(); i++) {
            bits[i * 3] = Float.floatToIntBits((float) way.get(i).x);
            bits[i * 3 + 1] = Float.floatToIntBits((float) way.get(i).y);
            bits[i * 3 + 2] = Float.floatToIntBits((float) way.get(i).z);
        }
        CompoundTag t = new CompoundTag();
        t.putIntArray("pts", bits);
        t.putFloat("from", from);
        t.putFloat("to", to);
        t.putLong("start", level().getGameTime());
        float top = SPEED[Math.floorMod(style(), SPEED.length)];
        t.putInt("time", from == to ? 1 : Math.max(20, Math.round(Math.abs(to - from) / top * 1.5F)));
        t.putBoolean("front", front);
        entityData.set(TRIP, t);
    }

    /**
     * Put on a line: its front at a rail, its cars along the line behind it (the longer way), standing.
     */
    public void placeAt(BlockPos rail) {
        Level level = level();
        List<List<BlockPos>> ways = TrainLine.bothWays(level, rail, 64);
        List<BlockPos> behind = ways.isEmpty() ? List.of() : ways.get(0);
        for (List<BlockPos> w : ways) {
            if (w.size() > behind.size()) {
                behind = w;
            }
        }
        List<Vec3> way = new ArrayList<>();
        for (int i = behind.size() - 1; i >= 0; i--) {
            way.add(TrainLine.centre(level, behind.get(i)));
        }
        way.add(TrainLine.centre(level, rail));
        if (way.size() == 1) {
            way.add(0, way.get(0).add(-1, 0, 0));
        }
        setTrip(way, 0.0F, 0.0F, true);
        float end = total();
        setTrip(way, end, end, true);
        Vec3 h = at(end);
        setPos(h.x, h.y, h.z);
    }

    /** The rail its front is on, and the one its back's on. */
    @Nullable
    private BlockPos railUnder(float s) {
        return TrainLine.railAt(level(), at(s));
    }

    /**
     * Sent to a rail on its line (a station's): the way there from its front, if it's ahead - or, if it's behind, from its
     * back, the train backing up. Returns whether it's on its line.
     */
    public boolean goTo(BlockPos target) {
        float h = head(0.0F);
        BlockPos front = railUnder(h), back = railUnder(h - length() + 0.5F);
        if (front == null) {
            return false;
        }
        if (front.equals(target)) {
            return true;
        }
        Vec3 behindPt = at(h - 1.0F);
        // the way ahead: away from the train (from its front); the way behind: away from it from its back
        List<BlockPos> ahead = null, astern = null;
        for (List<BlockPos> w : TrainLine.bothWays(level(), front, TrainLine.MOST)) {
            if (w.get(0).getCenter().distanceToSqr(behindPt) > front.getCenter().distanceToSqr(behindPt) - 0.01) {
                ahead = w;
            }
        }
        if (back != null) {
            Vec3 frontPt = at(h - length() + 1.5F);
            for (List<BlockPos> w : TrainLine.bothWays(level(), back, TrainLine.MOST)) {
                if (w.get(0).getCenter().distanceToSqr(frontPt) > back.getCenter().distanceToSqr(frontPt) - 0.01) {
                    astern = w;
                }
            }
        }
        int ia = ahead == null ? -1 : ahead.indexOf(target), ib = astern == null ? -1 : astern.indexOf(target);
        if (ia < 0 && ib < 0) {
            return false;
        }
        boolean forward = ia >= 0 && (ib < 0 || ia <= ib);
        List<Vec3> way = new ArrayList<>();
        float from;
        boolean loco = locoFront();
        if (forward) {
            // what's behind its front (its cars' way), then on to the target
            float keep = length() + 2.0F;
            parse();
            for (int i = 0; i < cum.length; i++) {
                if (cum[i] >= h - keep && cum[i] < h) {
                    way.add(new Vec3(pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2]));
                }
            }
            way.add(at(h));
            from = 0.0F;
            for (int i = 1; i < way.size(); i++) {
                from += (float) way.get(i).distanceTo(way.get(i - 1));
            }
            for (int i = 0; i <= ia; i++) {
                way.add(TrainLine.centre(level(), ahead.get(i)));
            }
        } else {
            // turned round: its way back from its front to its back, then on behind it to the target; its back now its front
            parse();
            way.add(at(h));
            for (int i = cum.length - 1; i >= 0; i--) {
                if (cum[i] < h && cum[i] > h - length()) {
                    way.add(new Vec3(pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2]));
                }
            }
            way.add(at(h - length()));
            from = 0.0F;
            for (int i = 1; i < way.size(); i++) {
                from += (float) way.get(i).distanceTo(way.get(i - 1));
            }
            for (int i = 0; i <= ib; i++) {
                way.add(TrainLine.centre(level(), astern.get(i)));
            }
            loco = !loco;
        }
        // (points too close together dropped: they'd make it twitch)
        List<Vec3> clean = new ArrayList<>();
        for (Vec3 v : way) {
            if (clean.isEmpty() || clean.get(clean.size() - 1).distanceToSqr(v) > 1e-4) {
                clean.add(v);
            }
        }
        setTrip(clean, 0.0F, 0.0F, loco);
        float to = total();
        // (where it is now, along the new way: as far along as its length behind its front was kept)
        float now = Math.min(from, to);
        setTrip(clean, now, to, loco);
        arrivedAt = Long.MIN_VALUE;
        level().playSound(null, getX(), getY(), getZ(), style() == 1 ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL,
                0.8F, style() == 1 ? 0.6F : 1.6F);
        return true;
    }

    /** The stations along its line, each way from its front: [ahead, behind] - nearest first. */
    public List<List<BlockPos>> stations() {
        List<List<BlockPos>> out = new ArrayList<>();
        float h = head(0.0F);
        BlockPos front = railUnder(h);
        if (front == null) {
            return out;
        }
        Vec3 behindPt = at(h - 1.0F);
        for (List<BlockPos> w : TrainLine.bothWays(level(), front, TrainLine.MOST)) {
            List<BlockPos> st = new ArrayList<>();
            for (BlockPos r : w) {
                BlockPos s = TrainLine.stationBy(level(), r);
                if (s != null && !st.contains(s)) {
                    st.add(s);
                }
            }
            boolean isAhead = w.get(0).getCenter().distanceToSqr(behindPt) > front.getCenter().distanceToSqr(behindPt) - 0.01;
            out.add(isAhead ? 0 : out.size(), st);
        }
        return out;
    }

    // ---- each tick ----

    @Override
    public void tick() {
        super.tick();
        Vec3[] u = unit(locoUnit(), 0.0F);
        setPos(u[0].x, u[0].y, u[0].z);
        if (level().isClientSide) {
            // (steam: smoke from its chimney, more the faster it goes)
            if (style() == 1 && random.nextFloat() < 0.25F + speed(0.0F) * 1.5F) {
                Vec3 n = noseDir(0.0F), c = u[0].add(n.scale(2.0)).add(0, 2.6, 0);
                level().addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y, c.z, -n.x * speed(0.0F) * 0.5,
                        0.06, -n.z * speed(0.0F) * 0.5);
            }
            return;
        }
        boolean moving = moving();
        if (wasMoving && !moving) {
            arrivedAt = level().getGameTime();
            level().playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1.0F, 1.2F);
        }
        wasMoving = moving;
        // going round: on to the next station ahead (none: back the other way)
        if (loop() && !moving && arrivedAt != Long.MIN_VALUE && level().getGameTime() - arrivedAt >= WAIT) {
            arrivedAt = level().getGameTime();
            BlockPos here = railUnder(head(0.0F));
            for (List<BlockPos> way : stations()) {
                boolean went = false;
                for (BlockPos s : way) {
                    BlockPos r = TrainLine.railBy(level(), s);
                    if (r != null && !r.equals(here) && goTo(r)) {
                        went = true;
                        break;
                    }
                }
                if (went) {
                    break;
                }
            }
        }
        if (arrivedAt == Long.MIN_VALUE && !moving && loop()) {
            arrivedAt = level().getGameTime();
        }
    }

    // ---- riders ----

    /** A car's seats: where (along it - its nose ahead - across it), from its middle; the locomotive one (by its look), its cars four. */
    public static float[][] seats(boolean loco, int style) {
        return loco ? new float[][] {{style == 1 ? -1.5F : 0.9F, 0.0F}} : new float[][] {{-1.1F, -0.45F}, {-1.1F, 0.45F}, {1.1F, -0.45F}, {1.1F, 0.45F}};
    }

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

    /** Where a rider sits (car * 8 + seat), as the server says. */
    private Integer seatOf(Entity rider) {
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

    /** Sits a rider in a unit (the first free seat), if there's one. */
    public boolean sit(Entity rider, int unit) {
        if (unit < 0 || unit >= units() || rider.isPassenger()) {
            return false;
        }
        int n = seats(unit == locoUnit(), style()).length;
        for (int s = 0; s < n; s++) {
            int key = unit * 8 + s;
            if (!seats.containsValue(key)) {
                seats.put(rider.getId(), key);
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
        return getPassengers().size() < units() * 4;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (seats.remove(passenger.getId()) != null && !level().isClientSide) {
            syncSeats();
        }
    }

    /** A seat's place in the world, now (and its car's way). */
    private Vec3 seatAt(int key, float partialTick) {
        int unit = Math.min(key / 8, units() - 1), s = key % 8;
        Vec3[] u = unit(unit, partialTick);
        float[][] all = seats(unit == locoUnit(), style());
        float[] seat = all[Math.min(s, all.length - 1)];
        Vec3 f = u[1], side = new Vec3(-f.z, 0, f.x).normalize();
        // (the locomotive backing up faces back: its seat's along its nose)
        float along = seat[0] * (unit == locoUnit() && !locoFront() ? -1 : 1);
        return u[0].add(f.scale(along)).add(side.scale(seat[1])).add(0, 0.62, 0);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        Integer key = seatOf(passenger);
        if (key == null) {
            key = locoUnit() * 8;
        }
        Vec3 p = seatAt(key, 0.0F);
        move.accept(passenger, p.x, p.y, p.z);
        passenger.fallDistance = 0.0F;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Integer key = seatOf(passenger);
        int unit = key == null ? 0 : Math.min(key / 8, units() - 1);
        Vec3[] u = unit(unit, 0.0F);
        Vec3 side = new Vec3(-u[1].z, 0, u[1].x).normalize();
        // (out the side with room)
        for (int sgn : new int[] {1, -1}) {
            Vec3 p = u[0].add(side.scale(1.9 * sgn));
            BlockPos b = BlockPos.containing(p);
            if (level().getBlockState(b).getCollisionShape(level(), b).isEmpty() && level().getBlockState(b.above()).getCollisionShape(level(), b.above()).isEmpty()) {
                return new Vec3(p.x, Math.floor(p.y) + (level().getBlockState(b.below()).getCollisionShape(level(), b.below()).isEmpty() ? 0.0 : 0.0), p.z);
            }
        }
        return u[0].add(0, 1.0, 0);
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
        return getBoundingBox().inflate(length() + 3.0, 3.0, length() + 3.0);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown() || player.getVehicle() == this) {
            if (level().isClientSide) {
                com.example.skyblocks.client.TrainScreen.open(this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide) {
            sit(player, locoUnit());
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
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
        // (where it is is worked out here, from its trip)
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160.0 * 160.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setStyle(tag.getInt("style"));
        setCars(tag.contains("cars") ? tag.getInt("cars") : 2);
        entityData.set(LOOP, tag.getBoolean("loop"));
        CompoundTag t = tag.getCompound("trip");
        // (saved mid-trip: back where it was going, standing)
        float to = t.getFloat("to");
        t.putFloat("from", to);
        t.putLong("start", 0L);
        t.putInt("time", 1);
        entityData.set(TRIP, t);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("style", style());
        tag.putInt("cars", cars());
        tag.putBoolean("loop", loop());
        tag.put("trip", trip().copy());
    }
}
