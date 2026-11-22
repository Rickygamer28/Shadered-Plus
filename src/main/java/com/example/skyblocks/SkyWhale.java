package com.example.skyblocks;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Sky Whale: rare; one at a time over the sea at dusk, flying slow wide loops. Feed it fish and climb on its back for
 * a ride (it turns the way you look); brush it (gently, with a brush) for a Starlight Scale now and then.
 */
public class SkyWhale extends SkyCreature {
    private static final double RADIUS = 36.0;
    private double angle;
    private int fed;
    private int scaleCooldown;

    public SkyWhale(EntityType<? extends SkyWhale> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 80.0).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FLYING_SPEED, 0.2).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (fed > 0) {
            fed--;
        }
        if (scaleCooldown > 0) {
            scaleCooldown--;
        }
        Entity rider = getFirstPassenger();
        if (rider instanceof Player player) {
            // ridden: it turns (slowly) the way its rider looks, and rises or dives with their look
            float yaw = rotlerp(getYRot(), player.getYRot(), 1.6F);
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
            double rad = Math.toRadians(yaw);
            double climb = -Math.sin(Math.toRadians(player.getXRot())) * 0.18;
            Vec3 want = new Vec3(-Math.sin(rad) * 0.32, climb, Math.cos(rad) * 0.32);
            setDeltaMovement(getDeltaMovement().lerp(want, 0.05));
            // (its loop moves with it: let off, it carries on round from here)
            home = position().subtract(Math.cos(angle) * RADIUS, Math.sin(angle * 0.37) * 6.0, Math.sin(angle) * RADIUS);
            return;
        }
        // slow, wide loops round where it came from, rising and falling a little
        Vec3 base = home != null ? home : position();
        angle += 0.22 / RADIUS;
        Vec3 target = base.add(Math.cos(angle) * RADIUS, Math.sin(angle * 0.37) * 6.0, Math.sin(angle) * RADIUS);
        steer(target, 0.22, 0.02);
        if (level() instanceof ServerLevel server && tickCount % 4 == 0) {
            // (a few sparks trail behind it)
            Vec3 back = position().subtract(getDeltaMovement().normalize().scale(4.0));
            server.sendParticles(ParticleTypes.END_ROD, back.x, back.y + 1.0, back.z, 1, 0.4, 0.3, 0.4, 0.0);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.TROPICAL_FISH)) {
            if (!level().isClientSide) {
                stack.consume(1, player);
                fed = 6000;
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.HEART, getX(), getY() + 2.2, getZ(), 5, 1.0, 0.4, 1.0, 0.0);
                }
                level().playSound(null, blockPosition(), SoundEvents.DOLPHIN_EAT, SoundSource.NEUTRAL, 1.0F, 0.6F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stack.is(Items.BRUSH)) {
            if (scaleCooldown > 0) {
                return InteractionResult.PASS;
            }
            if (!level().isClientSide) {
                scaleCooldown = 6000;
                spawnAtLocation(new ItemStack(ModItems.STARLIGHT_SCALE.get()), 1.0F);
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                level().playSound(null, blockPosition(), SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stack.isEmpty() && fed > 0 && !isVehicle()) {
            if (!level().isClientSide) {
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0, 1.85, 0.0);
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return null; // (it flies itself; its rider only sways it)
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(4.0); // (its tail and head reach well past its box)
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.NOTE_BLOCK_DIDGERIDOO.value();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 300;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F + random.nextFloat() * 0.1F;
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Fed", fed);
        tag.putInt("ScaleCooldown", scaleCooldown);
        tag.putDouble("Angle", angle);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fed = tag.getInt("Fed");
        scaleCooldown = tag.getInt("ScaleCooldown");
        angle = tag.getDouble("Angle");
    }
}
