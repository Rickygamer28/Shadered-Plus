package com.example.skyblocks;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A line of sky drawn in the air with the sky wand (SkyWandItem): its points (from where it starts), the sky it shows
 * (Shadered's sky type and pass, by name), and how long it lasts - a minute, fading at the end, or for good. Drawn as a
 * ribbon turned to face you, a window into its sky (client/SkyTrailRenderer).
 */
public class SkyTrailEntity extends Entity {
    /** The most points one line holds (a longer stroke carries on in another). */
    public static final int MAX_POINTS = 240;
    /** How long a fading line lasts (ticks), and how long its fading takes at the end. */
    public static final int LIFE = 1200, FADE = 60;

    private static final EntityDataAccessor<CompoundTag> POINTS = SynchedEntityData.defineId(SkyTrailEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<String> SKY = SynchedEntityData.defineId(SkyTrailEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> PASS = SynchedEntityData.defineId(SkyTrailEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> KEEP = SynchedEntityData.defineId(SkyTrailEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DRAWING = SynchedEntityData.defineId(SkyTrailEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(SkyTrailEntity.class, EntityDataSerializers.INT);

    /** The points (x, y, z from the entity's own position), as the server has them (and the client, as last sent). */
    private float[] points = new float[0];
    @Nullable
    private UUID owner;
    private int age;

    public SkyTrailEntity(EntityType<? extends SkyTrailEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(POINTS, new CompoundTag());
        builder.define(SKY, "SPACE");
        builder.define(PASS, "NORMAL");
        builder.define(KEEP, false);
        builder.define(DRAWING, false);
        builder.define(AGE, 0);
    }

    /** A new line, starting at `at`. */
    public static SkyTrailEntity start(Level level, Vec3 at, String sky, String pass, boolean keep, @Nullable UUID owner) {
        SkyTrailEntity e = new SkyTrailEntity(ModEntities.SKY_TRAIL.get(), level);
        e.setPos(at);
        e.entityData.set(SKY, sky);
        e.entityData.set(PASS, pass);
        e.entityData.set(KEEP, keep);
        e.entityData.set(DRAWING, true);
        e.owner = owner;
        e.add(at);
        return e;
    }

    public String sky() {
        return entityData.get(SKY);
    }

    public String pass() {
        return entityData.get(PASS);
    }

    public boolean keep() {
        return entityData.get(KEEP);
    }

    public boolean drawing() {
        return entityData.get(DRAWING);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    /** How many ticks it has been about (as the server last said). */
    public int age() {
        return entityData.get(AGE);
    }

    public float[] points() {
        return points;
    }

    public int count() {
        return points.length / 3;
    }

    public boolean full() {
        return count() >= MAX_POINTS;
    }

    /** Where its last point is (in the world). */
    public Vec3 last() {
        int n = points.length;
        return n < 3 ? position() : position().add(points[n - 3], points[n - 2], points[n - 1]);
    }

    /** Another point on the line (in the world); false if it's full. */
    public boolean add(Vec3 at) {
        if (full()) {
            return false;
        }
        float[] p = java.util.Arrays.copyOf(points, points.length + 3);
        p[points.length] = (float) (at.x - getX());
        p[points.length + 1] = (float) (at.y - getY());
        p[points.length + 2] = (float) (at.z - getZ());
        points = p;
        sync();
        return true;
    }

    /** Done drawing: a line too short to see is let go. */
    public void finish() {
        entityData.set(DRAWING, false);
        if (count() < 2) {
            discard();
        }
    }

    private void sync() {
        int[] bits = new int[points.length];
        for (int i = 0; i < points.length; i++) {
            bits[i] = Float.floatToIntBits(points[i]);
        }
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("p", bits);
        entityData.set(POINTS, tag);
        setBoundingBox(makeBoundingBox());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (POINTS.equals(key)) {
            int[] bits = entityData.get(POINTS).getIntArray("p");
            float[] p = new float[bits.length - bits.length % 3];
            for (int i = 0; i < p.length; i++) {
                p[i] = Float.intBitsToFloat(bits[i]);
            }
            points = p;
            setBoundingBox(makeBoundingBox());
        } else if (AGE.equals(key)) {
            clientAge = entityData.get(AGE);
        }
    }

    /** All of the line (for what's in view, and where it's tracked from). */
    @Override
    protected AABB makeBoundingBox() {
        double x0 = getX(), y0 = getY(), z0 = getZ(), x1 = x0, y1 = y0, z1 = z0;
        float[] p = points;
        if (p != null) {
            for (int i = 0; i + 2 < p.length; i += 3) {
                x0 = Math.min(x0, getX() + p[i]);
                x1 = Math.max(x1, getX() + p[i]);
                y0 = Math.min(y0, getY() + p[i + 1]);
                y1 = Math.max(y1, getY() + p[i + 1]);
                z0 = Math.min(z0, getZ() + p[i + 2]);
                z1 = Math.max(z1, getZ() + p[i + 2]);
            }
        }
        return new AABB(x0, y0, z0, x1, y1, z1).inflate(0.25);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientAge++;
            return;
        }
        age++;
        if (age % 20 == 0) {
            entityData.set(AGE, age);
        }
        if (drawing() && age > 20 * 60 * 5) {
            finish(); // (a stroke never finished: its wand gone)
        }
        if (!keep() && !drawing() && age > LIFE) {
            discard();
        }
    }

    /** Its age as the client has it, carried on between the server's word (every second). */
    public int clientAge;

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return keep() && !isRemoved(); // (a fading line goes with the session)
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160.0 * 160.0;
    }

    /** Whether the line passes within `reach` of the ray from `from` along `dir` (unit), at most `length` along it. */
    public boolean near(Vec3 from, Vec3 dir, double length, double reach) {
        float[] p = points;
        for (int i = 0; i + 2 < p.length; i += 3) {
            Vec3 at = new Vec3(getX() + p[i], getY() + p[i + 1], getZ() + p[i + 2]);
            Vec3 d = at.subtract(from);
            double t = d.dot(dir);
            if (t < 0 || t > length) {
                continue;
            }
            if (d.subtract(dir.scale(t)).lengthSqr() < reach * reach) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(SKY, tag.getString("sky").isEmpty() ? "SPACE" : tag.getString("sky"));
        entityData.set(PASS, tag.getString("pass").isEmpty() ? "NORMAL" : tag.getString("pass"));
        entityData.set(KEEP, tag.getBoolean("keep"));
        entityData.set(DRAWING, false);
        age = tag.getInt("age");
        entityData.set(AGE, age);
        if (tag.hasUUID("owner")) {
            owner = tag.getUUID("owner");
        }
        int[] bits = tag.getIntArray("points");
        float[] p = new float[Math.min(MAX_POINTS * 3, bits.length - bits.length % 3)];
        for (int i = 0; i < p.length; i++) {
            p[i] = Float.intBitsToFloat(bits[i]);
        }
        points = p;
        sync();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("sky", sky());
        tag.putString("pass", pass());
        tag.putBoolean("keep", keep());
        tag.putInt("age", age);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        int[] bits = new int[points.length];
        for (int i = 0; i < points.length; i++) {
            bits[i] = Float.floatToIntBits(points[i]);
        }
        tag.putIntArray("points", bits);
    }
}
