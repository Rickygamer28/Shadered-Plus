package com.example.skyblocks;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A sky creature (SkyJellyfish, SkyWhale, SkyMoth): a peaceful flying mob whose body shows a sky - its own, picked at
 * random from every sky there is (so skies added later turn up too), unless it's given one. It floats (no gravity, no
 * fall damage) and steers itself towards a point it picks ({@link #steer}); one that came by itself leaves by day.
 */
public abstract class SkyCreature extends PathfinderMob {
    private static final EntityDataAccessor<String> SKY = SynchedEntityData.defineId(SkyCreature.class, EntityDataSerializers.STRING);

    /** Where it keeps near (where it came to be). */
    @Nullable
    protected Vec3 home;
    /** Came by itself (not made by a player, nor from an egg): leaves by day. */
    protected boolean wild;

    protected SkyCreature(EntityType<? extends SkyCreature> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKY, "");
    }

    /** Its sky (a random one if it has none yet). */
    public SkyblockType sky() {
        String name = entityData.get(SKY);
        SkyblockType[] skies = SkyblockType.values();
        for (SkyblockType sky : skies) {
            if (sky.name().equals(name)) {
                return sky;
            }
        }
        return skies[Math.floorMod(getUUID().hashCode(), skies.length)];
    }

    public void setSky(@Nullable SkyblockType sky) {
        entityData.set(SKY, sky == null ? "" : sky.name());
    }

    public void setHome(Vec3 home) {
        this.home = home;
    }

    public void setWild(boolean wild) {
        this.wild = wild;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data) {
        if (entityData.get(SKY).isEmpty()) {
            SkyblockType[] skies = SkyblockType.values();
            setSky(skies[random.nextInt(skies.length)]);
        }
        if (home == null) {
            home = position();
        }
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    /** Eases its motion towards going at speed towards target (and keeps it facing the way it goes). */
    protected void steer(Vec3 target, double speed, double ease) {
        Vec3 to = target.subtract(position());
        double d = to.length();
        Vec3 want = d < 1.0E-3 ? Vec3.ZERO : to.scale(Math.min(speed, d * 0.2) / d);
        setDeltaMovement(getDeltaMovement().lerp(want, ease));
        Vec3 v = getDeltaMovement();
        if (v.horizontalDistanceSqr() > 1.0E-5) {
            float yaw = (float) (Math.atan2(-v.x, v.z) * 180.0 / Math.PI);
            setYRot(rotlerp(getYRot(), yaw, 6.0F));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
    }

    /** One that came by itself, by day: fades away now and then. */
    protected boolean leaveByDay() {
        return wild && !isPersistenceRequired() && level().isDay() && !isVehicle() && random.nextInt(400) == 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && leaveByDay()) {
            discard();
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    public boolean removeWhenFarAway(double distance) {
        return wild && !isVehicle();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Sky", entityData.get(SKY));
        tag.putBoolean("Wild", wild);
        if (home != null) {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SKY, tag.getString("Sky"));
        wild = tag.getBoolean("Wild");
        if (tag.contains("HomeX")) {
            home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        }
    }
}
