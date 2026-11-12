package com.example.skyblocks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown ball of yarn: it bounces, rolls and slows to a stop, and a cat companion chases it and bats it about.
 * Right-click (or hit) it to pick it up again. Client: rolls as it goes (rollAngle about rollAxis).
 */
public class YarnBallEntity extends Entity {
    /** Client: how far it's rolled, and about which axis (x, z). */
    public float roll, rollO, axisX = 1, axisZ;

    public YarnBallEntity(EntityType<? extends YarnBallEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            double dx = getX() - xo, dz = getZ() - zo, d = Math.sqrt(dx * dx + dz * dz);
            rollO = roll;
            if (d > 1.0E-4) {
                roll += (float) (d / 0.14);
                axisX = (float) (dz / d);
                axisZ = (float) (-dx / d);
            }
            return;
        }
        Vec3 v = getDeltaMovement().add(0, -0.04, 0);
        Vec3 before = v;
        move(MoverType.SELF, v);
        Vec3 now = getDeltaMovement();
        if (horizontalCollision) {
            // (off a wall: back the other way, slower)
            now = new Vec3(Math.abs(before.x) > 1.0E-3 && Math.abs(getX() - xo) < Math.abs(before.x) * 0.5 ? -before.x * 0.5 : now.x, now.y,
                    Math.abs(before.z) > 1.0E-3 && Math.abs(getZ() - zo) < Math.abs(before.z) * 0.5 ? -before.z * 0.5 : now.z);
        }
        if (verticalCollision && before.y < 0) {
            double up = -before.y * 0.15;
            now = new Vec3(now.x * 0.75, up > 0.12 ? up : 0, now.z * 0.75);
            if (up > 0.12) {
                playSound(SoundEvents.WOOL_STEP, 0.3F, 1.5F);
            }
        }
        if (onGround()) {
            now = new Vec3(now.x * 0.86, now.y, now.z * 0.86);
        }
        setDeltaMovement(now.scale(0.98));
        if (getY() < level().getMinBuildHeight() - 32) {
            discard();
        }
    }

    /** A cat's paw: off it goes. */
    public void bat(double x, double y, double z) {
        setDeltaMovement(getDeltaMovement().add(x, y, z));
        hasImpulse = true;
        playSound(SoundEvents.WOOL_HIT, 0.4F, 1.4F);
    }

    /** Has it come to a stop? */
    public boolean still() {
        return getDeltaMovement().horizontalDistanceSqr() < 0.0004 && onGround();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide) {
            ItemStack ball = new ItemStack(ModItems.YARN_BALL.get());
            if (!player.getInventory().add(ball)) {
                spawnAtLocation(ball);
            }
            playSound(SoundEvents.ITEM_PICKUP, 0.3F, 1.2F);
            discard();
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !isRemoved()) {
            if (!(source.getEntity() instanceof Player p && p.isCreative())) {
                spawnAtLocation(new ItemStack(ModItems.YARN_BALL.get()));
            }
            discard();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
