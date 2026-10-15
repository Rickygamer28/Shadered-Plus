package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A big hostile monster with one melee attack: a lunging bite that ends in a two-paw slam (the model's attack clip,
 * 1.7 s). The hit lands at the lunge, on everything in a box in front of it.
 */
public class ZinogreEntity extends Monster {
    /** Length of the attack (the model's "attack" clip: 1.7 s). */
    public static final int ATTACK_TICKS = 34;
    /** When the lunge reaches forward (0.65 s into the clip). */
    public static final int HIT_TICK = 13;
    /** Entity event telling clients to play the attack (the same id iron golems and ravagers use for theirs). */
    static final byte ATTACK_EVENT = 4;

    /** Ticks since the current attack started, or -1 when not attacking (server and client). */
    public int attackTick = -1;
    /** Client: tickCount when the last attack started (for the animation). */
    public int attackStartTick = Integer.MIN_VALUE / 2;
    /** Client only: the renderer's per-mob state (model copy and animation player). */
    public Object clientState;

    public ZinogreEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ZinogreAttackGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.45));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean isAttacking() {
        return attackTick >= 0;
    }

    /** Server: starts the attack and tells clients to play it. */
    void startAttack() {
        attackTick = 0;
        level().broadcastEntityEvent(this, ATTACK_EVENT);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == ATTACK_EVENT) {
            attackTick = 0;
            attackStartTick = tickCount;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (attackTick >= 0) {
            attackTick++;
            if (attackTick == HIT_TICK && !level().isClientSide && isAlive()) {
                hitInFront();
            }
            if (attackTick >= ATTACK_TICKS) {
                attackTick = -1;
            }
        }
    }

    /** Hurts everything (but other Zinogres) in a box in front of the head, where the lunge reaches. */
    private void hitInFront() {
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 center = position().add(forward.scale(2.6)).add(0.0, 1.2, 0.0);
        AABB box = new AABB(center, center).inflate(1.8, 1.4, 1.8);
        boolean hit = false;
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof ZinogreEntity))) {
            hit |= doHurtTarget(target);
        }
        playSound(hit ? SoundEvents.RAVAGER_ATTACK : SoundEvents.RAVAGER_STEP, 1.5F, 0.8F);
    }

    /** The model is longer than the hitbox (head in front, tail far behind): keep drawing it while any of it shows. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(3.0, 0.5, 3.0);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WOLF_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 0.6F, 0.9F);
    }

    @Override
    public float getVoicePitch() {
        return 0.55F + random.nextFloat() * 0.1F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }
}
