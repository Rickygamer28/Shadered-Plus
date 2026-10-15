package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.joml.Vector3f;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
 * piece; hearts when it works, smoke when it doesn't); it stays standing and starts following its new owner. Tamed it follows its owner, fights what the owner fights or
 * what hurts the owner, heals 10 health per piece of meat, and lies down / gets up when the owner right-clicks it.
 *
 * Its one melee attack is a lunging bite that ends in a two-paw slam (the model's attack clip, 1.7 s); the hit lands
 * at the lunge, on everything in a box in front of it (but never its owner or the owner's other pets).
 *
 * Breakable parts, like in Monster Hunter: damage goes to the part facing the attacker (head: a horn, depending on the
 * side; flanks: the shoulders; behind: the tail; the front legs take a share of hits from the front and sides). When a
 * part has taken enough it breaks (horn snapped, tail cut, spikes broken off) and shows a fresh wound; on a tamed one
 * the wounds heal into stumps once it is back at full health. Broken parts never grow back. A part that breaks off
 * drops as an item (horn, tail, claw, shell). Like mob heads, its head drops when a charged creeper kills it.
 *
 * Charge-up, its signature move: in a fight it crouches and howls (the model's charge clip, 4.3 s), and halfway
 * through it becomes charged for 30 s: its shell glows (blue; red for the Stygian), sparks fly off it, it moves
 * faster and hits half again as hard, and its hits sometimes call down lightning (just for show: no fire, no extra
 * damage). Breaking a part while it charges interrupts the charge. After the charge wears off it can charge again
 * 10 s later.
 *
 * The Stygian Zinogre is the same mob with its own entity type: tougher, darker.
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

    /** Length of the charge-up (the model's "charge" clip: 4.3 s) and when the howl makes it charged. */
    public static final int CHARGE_TICKS = 87;
    private static final int CHARGED_AT = 44;
    /** How long a charge lasts, and the wait before the next one. */
    private static final int CHARGE_DURATION = 600;
    private static final int CHARGE_COOLDOWN = 200;
    /** Entity events: start the charge-up animation / stop it (interrupted). */
    static final byte CHARGE_EVENT = 72, CHARGE_CANCEL_EVENT = 73;
    private static final EntityDataAccessor<Boolean> CHARGED = SynchedEntityData.defineId(ZinogreEntity.class, EntityDataSerializers.BOOLEAN);
    private static final ResourceLocation CHARGED_DAMAGE = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "charged_damage");
    private static final ResourceLocation CHARGED_SPEED = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "charged_speed");
    private static final DustParticleOptions RED_SPARK = new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.2F), 0.8F);

    /** Ticks since the charge-up started, or -1 (server and client). */
    public int chargeTick = -1;
    /** Client: tickCount when the last charge-up started (for the animation); moved far back when interrupted. */
    public int chargeStartTick = Integer.MIN_VALUE / 2;
    /** Server: charged ticks left, and the wait before it may charge again. */
    private int chargedLeft;
    int chargeCooldown;

    /** Breakable part groups (the same numbers as the model's mesh pieces). */
    public static final int HORN_L = 1, HORN_R = 2, TAIL = 3, TAIL_FUR = 4, ARMS = 5, SHOULDERS = 6, EYELIDS = 7;
    /** Part states. */
    public static final int INTACT = 0, BROKEN = 1, HEALED = 2;
    private static final int[] BREAKABLE = {HORN_L, HORN_R, TAIL, ARMS, SHOULDERS};
    /** Damage a part takes before it breaks, for a 200-health Zinogre (scaled with max health). */
    private static final float[] BREAK_AT = {0, 35, 35, 55, 0, 45, 45};
    /** Each part's state, 2 bits per group (group g at bits 2(g-1)). */
    private static final EntityDataAccessor<Integer> PARTS = SynchedEntityData.defineId(ZinogreEntity.class, EntityDataSerializers.INT);
    /** Damage each part has taken so far (server). */
    private final float[] partDamage = new float[7];

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

    public static AttributeSupplier.Builder createAttributes(boolean stygian) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, stygian ? 260.0 : 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, stygian ? 18.0 : 14.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ARMOR, stygian ? 9.0 : 6.0)
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

    public boolean isStygian() {
        return getType() == ZinogreMod.STYGIAN_ZINOGRE.get();
    }

    // ---------------------------------------------------------------- breakable parts

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PARTS, 0);
        builder.define(CHARGED, false);
    }

    // ---------------------------------------------------------------- charge-up

    public boolean isCharged() {
        return entityData.get(CHARGED);
    }

    public boolean isCharging() {
        return chargeTick >= 0;
    }

    /** Whether it would start a charge-up now (in a fight, not charged, not busy, cooldown over). */
    boolean canCharge() {
        return !isCharged() && !isCharging() && !isAttacking() && chargeCooldown <= 0 && !isOrderedToSit();
    }

    /** Server: starts the charge-up and tells clients to play it. */
    void startCharge() {
        chargeTick = 0;
        navigation.stop();
        level().broadcastEntityEvent(this, CHARGE_EVENT);
        playSound(SoundEvents.WOLF_GROWL, 2.0F, 0.5F);
    }

    private void cancelCharge() {
        if (chargeTick >= 0) {
            chargeTick = -1;
            chargeCooldown = CHARGE_COOLDOWN / 2;
            level().broadcastEntityEvent(this, CHARGE_CANCEL_EVENT);
        }
    }

    private void becomeCharged() {
        entityData.set(CHARGED, true);
        chargedLeft = CHARGE_DURATION;
        modifier(Attributes.ATTACK_DAMAGE, CHARGED_DAMAGE, 0.5);
        modifier(Attributes.MOVEMENT_SPEED, CHARGED_SPEED, 0.15);
        playSound(SoundEvents.WOLF_HOWL, 3.0F, 0.45F);
        playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 1.6F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(sparkParticle(), getX(), getY() + 1.4, getZ(), 120, 1.4, 0.9, 1.4, 0.2);
        }
    }

    private void endCharge() {
        entityData.set(CHARGED, false);
        chargedLeft = 0;
        chargeCooldown = CHARGE_COOLDOWN;
        removeModifier(Attributes.ATTACK_DAMAGE, CHARGED_DAMAGE);
        removeModifier(Attributes.MOVEMENT_SPEED, CHARGED_SPEED);
        playSound(SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.4F);
    }

    private void modifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null && !instance.hasModifier(id)) {
            instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private void removeModifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    private ParticleOptions sparkParticle() {
        return isStygian() ? RED_SPARK : ParticleTypes.ELECTRIC_SPARK;
    }

    /** While charged: sparks crackle off its back (client) and the charge runs down (server). */
    private void chargedTick() {
        if (level().isClientSide) {
            if (isCharged() && random.nextInt(2) == 0) {
                Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot);
                double along = (random.nextDouble() - 0.4) * 4.0;
                level().addParticle(sparkParticle(), getX() + forward.x * along + (random.nextDouble() - 0.5) * 1.6,
                        getY() + 1.2 + random.nextDouble() * 1.2, getZ() + forward.z * along + (random.nextDouble() - 0.5) * 1.6,
                        (random.nextDouble() - 0.5) * 0.2, 0.1, (random.nextDouble() - 0.5) * 0.2);
            }
            return;
        }
        if (chargeCooldown > 0) {
            chargeCooldown--;
        }
        if (isCharged()) {
            if (--chargedLeft <= 0 || isOrderedToSit()) {
                endCharge();
            } else if (random.nextInt(80) == 0) {
                playSound(SoundEvents.BEEHIVE_WORK, 1.0F, 1.8F); // crackle
            }
        }
    }

    /** Charged hits sometimes call down lightning on the target (visual only). */
    private void chargedHit(Entity target) {
        if (isCharged() && random.nextInt(3) == 0 && level() instanceof ServerLevel server) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
            if (bolt != null) {
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                bolt.setVisualOnly(true);
                server.addFreshEntity(bolt);
            }
        }
    }

    /** All part states, packed (see {@link #partState}). */
    public int parts() {
        return entityData.get(PARTS);
    }

    public static int partState(int parts, int group) {
        return (parts >> (2 * (group - 1))) & 3;
    }

    private void setPartState(int group, int state) {
        int shift = 2 * (group - 1);
        entityData.set(PARTS, (parts() & ~(3 << shift)) | (state << shift));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive() && source.getSourcePosition() != null) {
            damageParts(source.getSourcePosition(), amount);
        }
        return hurt;
    }

    /** Sends the damage to the part(s) facing where it came from. */
    private void damageParts(Vec3 from, float amount) {
        double dx = from.x - getX(), dz = from.z - getZ();
        float yaw = yBodyRot * ((float) Math.PI / 180.0F);
        double forward = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
        double right = -dx * Math.cos(yaw) - dz * Math.sin(yaw);
        double angle = Math.toDegrees(Math.atan2(right, forward)); // 0 = straight ahead, +-180 = behind
        double abs = Math.abs(angle);
        if (abs <= 50) {
            damagePart(angle > 0 ? HORN_R : HORN_L, amount);
            damagePart(ARMS, amount * 0.4F);
        } else if (abs <= 120) {
            damagePart(SHOULDERS, amount);
            damagePart(ARMS, amount * 0.6F);
        } else {
            damagePart(TAIL, amount);
        }
    }

    private void damagePart(int group, float amount) {
        if (partState(parts(), group) != INTACT) {
            return;
        }
        partDamage[group] += amount;
        if (partDamage[group] >= BREAK_AT[group] * getMaxHealth() / 200.0F) {
            breakPart(group);
        }
    }

    private void breakPart(int group) {
        setPartState(group, BROKEN);
        attackTick = -1; // flinches out of its attack
        cancelCharge(); // and out of a charge-up
        Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot);
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3 at = switch (group) {
            case HORN_L -> forward.scale(2.3).add(side.scale(-0.15)).add(0, 1.2, 0);
            case HORN_R -> forward.scale(2.3).add(side.scale(0.15)).add(0, 1.2, 0);
            case TAIL -> forward.scale(-3.0).add(0, 0.8, 0);
            case ARMS -> forward.scale(1.3).add(0, 0.4, 0);
            default -> forward.scale(0.9).add(0, 1.8, 0);
        };
        at = at.add(position());
        // the part that broke off drops where it was (both horns: one horn each)
        ItemEntity drop = new ItemEntity(level(), at.x, at.y, at.z, new ItemStack(partItem(group)));
        drop.setDeltaMovement((random.nextDouble() - 0.5) * 0.3, 0.3, (random.nextDouble() - 0.5) * 0.3);
        drop.setDefaultPickUpDelay();
        level().addFreshEntity(drop);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0.3);
            server.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.1);
        }
        level().playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BREAK, getSoundSource(), 2.0F, 0.6F);
        playSound(SoundEvents.RAVAGER_STUNNED, 1.8F, 0.7F);
    }

    private net.minecraft.world.item.Item partItem(int group) {
        boolean s = isStygian();
        return (switch (group) {
            case HORN_L, HORN_R -> s ? ZinogreMod.STYGIAN_HORN : ZinogreMod.HORN;
            case TAIL -> s ? ZinogreMod.STYGIAN_TAIL : ZinogreMod.TAIL;
            case ARMS -> s ? ZinogreMod.STYGIAN_CLAW : ZinogreMod.CLAW;
            default -> s ? ZinogreMod.STYGIAN_SHELL : ZinogreMod.SHELL;
        }).get();
    }

    /** Like mob heads: killed by a charged creeper's explosion, it drops its head (one head per creeper). */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (source.getEntity() instanceof Creeper creeper && creeper.canDropMobsSkull()) {
            creeper.increaseDroppedSkulls();
            spawnAtLocation(new ItemStack(isStygian() ? ZinogreMod.STYGIAN_HEAD_ITEM.get() : ZinogreMod.HEAD_ITEM.get()));
        }
    }

    /** A tamed one's wounds heal into stumps once it is back at full health. */
    private void healWounds() {
        for (int group : BREAKABLE) {
            if (partState(parts(), group) == BROKEN) {
                setPartState(group, HEALED);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Parts", parts());
        for (int group : BREAKABLE) {
            tag.putFloat("PartDamage" + group, partDamage[group]);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PARTS, tag.getInt("Parts"));
        for (int group : BREAKABLE) {
            partDamage[group] = tag.getFloat("PartDamage" + group);
        }
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
        } else if (id == CHARGE_EVENT) {
            chargeTick = 0;
            chargeStartTick = tickCount;
        } else if (id == CHARGE_CANCEL_EVENT) {
            chargeTick = -1;
            chargeStartTick = Integer.MIN_VALUE / 2;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        chargedTick();
        if (chargeTick >= 0) {
            chargeTick++;
            if (chargeTick == CHARGED_AT && !level().isClientSide && isAlive()) {
                becomeCharged();
            }
            if (chargeTick >= CHARGE_TICKS) {
                chargeTick = -1;
            }
        }
        if (!level().isClientSide && isTame() && tickCount % 20 == 0 && getHealth() >= getMaxHealth()) {
            healWounds();
        }
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
        Vec3 center = position().add(forward.scale(3.0)).add(0.0, 1.2, 0.0);
        AABB box = new AABB(center, center).inflate(1.8, 1.4, 1.8);
        boolean hit = false;
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !isFriend(e))) {
            if (doHurtTarget(target)) {
                hit = true;
                chargedHit(target);
            }
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
