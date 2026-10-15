package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

/**
 * A big tamable monster, like a giant wolf.
 *
 * Untamed it wanders and only fights back when hurt. Feed it raw or cooked meat to tame it (1 in 4 chance per
 * piece; hearts when it works, smoke when it doesn't). Tamed it follows its owner, fights what the owner fights or
 * what hurts the owner, heals 10 health per piece of meat, and lies down / gets up when the owner right-clicks it.
 *
 * Its one melee attack is a lunging bite that ends in a two-paw slam (the model's attack clip, 1.7 s); the hit lands
 * at the lunge, on everything in a box in front of it (but never its owner or the owner's other pets).
 */
public class ZinogreEntity extends TamableAnimal {
    /** Length of the attack (the model's "attack" clip: 1.7 s). */
    public static final int ATTACK_TICKS = 34;
    /** When the lunge reaches forward (0.65 s into the clip). */
    public static final int HIT_TICK = 13;
    /** Entity event telling clients to play the attack (the same id iron golems and ravagers use for theirs). */
    static final byte ATTACK_EVENT = 4;
    /** 1 in this many pieces of meat tames it. */
    private static final int TAME_CHANCE = 4;

    /** Ticks since the current attack started, or -1 when not attacking (server and client). */
    public int attackTick = -1;
    /** Client: tickCount when the last attack started (for the animation). */
    public int attackStartTick = Integer.MIN_VALUE / 2;
    /** Client only: the renderer's per-mob state (model copy and animation player). */
    public Object clientState;

    public ZinogreEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
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
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(2, new ZinogreAttackGoal(this));
        goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.0, 12.0F, 4.0F));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.45));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
    }

    // ---------------------------------------------------------------- taming and sitting

    /** Raw or cooked meat tames it and heals it. */
    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.MEAT);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isTame()) {
            if (isOwnedBy(player) && isFood(stack) && getHealth() < getMaxHealth()) {
                if (!level().isClientSide) {
                    stack.consume(1, player);
                    heal(10.0F);
                    playSound(SoundEvents.GENERIC_EAT, 1.0F, 0.7F);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (isOwnedBy(player) && !isFood(stack)) {
                if (!level().isClientSide) {
                    setOrderedToSit(!isOrderedToSit());
                    jumping = false;
                    navigation.stop();
                    setTarget(null);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            return super.mobInteract(player, hand);
        }
        if (isFood(stack)) {
            if (!level().isClientSide) {
                stack.consume(1, player);
                if (random.nextInt(TAME_CHANCE) == 0 && !EventHooks.onAnimalTame(this, player)) {
                    tame(player);
                    navigation.stop();
                    setTarget(null);
                    setOrderedToSit(true);
                    level().broadcastEntityEvent(this, (byte) 7); // hearts
                } else {
                    level().broadcastEntityEvent(this, (byte) 6); // smoke
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    /** No breeding. */
    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal other) {
        return false;
    }

    // ---------------------------------------------------------------- attack

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

    /** Hurts everything in a box in front of the head, where the lunge reaches (not friends, see {@link #isFriend}). */
    private void hitInFront() {
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 center = position().add(forward.scale(2.6)).add(0.0, 1.2, 0.0);
        AABB box = new AABB(center, center).inflate(1.8, 1.4, 1.8);
        boolean hit = false;
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !isFriend(e))) {
            hit |= doHurtTarget(target);
        }
        playSound(hit ? SoundEvents.RAVAGER_ATTACK : SoundEvents.RAVAGER_STEP, 1.5F, 0.8F);
    }

    /** Other Zinogres, and when tamed its owner and the owner's other pets, unless that's what it is fighting. */
    private boolean isFriend(LivingEntity e) {
        if (e == getTarget()) {
            return false;
        }
        if (e instanceof ZinogreEntity) {
            return true;
        }
        LivingEntity owner = getOwner();
        if (owner == null) {
            return false;
        }
        return e == owner || (e instanceof OwnableEntity pet && pet.getOwner() == owner);
    }

    /** A tamed one never attacks its owner, nor the owner's other pets. */
    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof OwnableEntity pet && pet.getOwner() == owner) {
            return false;
        }
        return !(target instanceof Player player && owner instanceof Player ownerPlayer && !ownerPlayer.canHarmPlayer(player));
    }

    // ---------------------------------------------------------------- the rest

    /** The model is longer than the hitbox (head in front, tail far behind): keep drawing it while any of it shows. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(3.0, 0.5, 3.0);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isInSittingPose() ? null : SoundEvents.WOLF_GROWL;
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
