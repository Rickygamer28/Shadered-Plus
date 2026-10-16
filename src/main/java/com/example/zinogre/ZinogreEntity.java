package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
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
 * the wounds heal into stumps once it is back at full health. Broken parts never grow back. Like mob heads, its head
 * drops when a charged creeper kills it.
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
    /** The big moves (besides the bite): roar when a fight starts, leap at a target farther away, backflip tail slam. */
    public static final int MOVE_NONE = 0, MOVE_ROAR = 1, MOVE_LEAP = 2, MOVE_FLIP = 3;
    /** Entity events starting each move (74 + move - 1). */
    static final byte MOVE_EVENT_BASE = 74;
    /** Entity event: the move was cut short (flinched when a part broke). */
    static final byte MOVE_CANCEL_EVENT = 77;
    /** Length of each move in ticks (the model's clips: roar 2.8 s, leap 1.7 s, flip 1.8 s). */
    private static final int[] MOVE_TICKS = {0, 57, 34, 37};
    /** Roar: when the roar comes out (slowing whatever is close). */
    private static final int ROAR_SHOUT = 14;
    private static final double ROAR_RADIUS = 10.0;
    /** Leap: when it leaves the ground, and the latest tick it counts as landed (it lands when it touches down). */
    private static final int LEAP_TAKEOFF = 5, LEAP_LAND = 26;
    /** Backflip: when the tail comes down. */
    private static final int FLIP_SLAM = 27;
    /** How long the death lasts (the model's death clip: 3.9 s, then it lies still for a moment). */
    public static final int DEATH_TICKS = 100;

    /** The move being done (MOVE_*), ticks into it (-1: none), server and client. */
    public int move = MOVE_NONE;
    public int moveTick = -1;
    /** Client: tickCount when the current move started (for the animation). */
    public int moveStartTick = Integer.MIN_VALUE / 2;
    /** Server: waits before the next roar / leap / flip. */
    private int roarCooldown, leapCooldown, flipCooldown;
    /** Server: where the leap is aimed. */
    @Nullable
    private Vec3 leapAt;
    private boolean leapLanded;

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
        return !isCharged() && !isCharging() && !isAttacking() && !isDoingMove() && chargeCooldown <= 0 && !isOrderedToSit();
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
        if (moveTick >= 0 && move != MOVE_LEAP) {
            moveTick = -1; // and out of a roar or flip (a leap can't stop in mid-air)
            move = MOVE_NONE;
            level().broadcastEntityEvent(this, MOVE_CANCEL_EVENT);
        }
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
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0.3);
            server.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.1);
        }
        level().playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BREAK, getSoundSource(), 2.0F, 0.6F);
        playSound(SoundEvents.RAVAGER_STUNNED, 1.8F, 0.7F);
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

    /** A tamed one grows its broken parts back once it is back at full health (heal it with meat). */
    private void healWounds() {
        boolean regrown = false;
        for (int group : BREAKABLE) {
            partDamage[group] = 0.0F;
            if (partState(parts(), group) != INTACT) {
                setPartState(group, INTACT);
                regrown = true;
            }
        }
        if (regrown && level() instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    getX(), getY() + getBbHeight() * 0.6, getZ(), 30, getBbWidth() * 0.6, getBbHeight() * 0.4, getBbWidth() * 0.6, 0.0);
            playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.6F);
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

    // ---------------------------------------------------------------- roar, leap, backflip

    public boolean isDoingMove() {
        return moveTick >= 0;
    }

    /** Busy with something it shouldn't be interrupted in (attack, charge-up, a move). */
    public boolean isBusy() {
        return isAttacking() || isCharging() || isDoingMove();
    }

    boolean canLeap() {
        return leapCooldown <= 0 && onGround() && !isBusy();
    }

    boolean canFlip() {
        return flipCooldown <= 0 && onGround() && !isBusy();
    }

    /** Server: starts a move and tells clients to play it. */
    void startMove(int which, @Nullable LivingEntity target) {
        move = which;
        moveTick = 0;
        navigation.stop();
        if (which == MOVE_LEAP) {
            leapAt = target != null ? target.position() : null;
            leapLanded = false;
            leapCooldown = 100 + random.nextInt(80);
            playSound(SoundEvents.WOLF_GROWL, 2.0F, 0.6F);
        } else if (which == MOVE_FLIP) {
            flipCooldown = 140 + random.nextInt(80);
        } else if (which == MOVE_ROAR) {
            roarCooldown = 600;
        }
        level().broadcastEntityEvent(this, (byte) (MOVE_EVENT_BASE + which - 1));
    }

    /**
     * Server: does a move right now, whatever it is doing (the /zinogre play command): roar, bite, leap (forward, at
     * its target if it has one), flip, charge or death (it dies).
     */
    public void playMove(String which) {
        attackTick = -1;
        moveTick = -1;
        move = MOVE_NONE;
        chargeTick = -1;
        switch (which) {
            case "roar" -> startMove(MOVE_ROAR, getTarget());
            case "leap" -> startMove(MOVE_LEAP, getTarget());
            case "flip" -> startMove(MOVE_FLIP, getTarget());
            case "bite" -> startAttack();
            case "charge" -> startCharge();
            case "death" -> kill();
            default -> { }
        }
    }

    /** Roars when it picks a fight (not again for a while). */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity old = getTarget();
        super.setTarget(target);
        if (!level().isClientSide && target != null && old == null && roarCooldown <= 0 && !isBusy()
                && isAlive() && !isOrderedToSit() && onGround()) {
            startMove(MOVE_ROAR, target);
        }
    }

    private void moveTick() {
        if (moveTick < 0) {
            return;
        }
        moveTick++;
        boolean server = !level().isClientSide && isAlive();
        if (server && move == MOVE_ROAR && moveTick == ROAR_SHOUT) {
            roar();
        }
        if (server && move == MOVE_LEAP && moveTick == LEAP_TAKEOFF) {
            takeOff();
        }
        if (server && move == MOVE_LEAP && !leapLanded && moveTick > LEAP_TAKEOFF + 3 && (onGround() || moveTick >= LEAP_LAND)) {
            leapLanded = true;
            slam(2.5, 2.4, 1.0F, 0.2); // lands on whatever it leapt at
        }
        if (server && move == MOVE_FLIP && moveTick == FLIP_SLAM) {
            slam(1.0, 3.6, 1.25F, 0.55);
        }
        if (move == MOVE_FLIP && level().isClientSide && moveTick == FLIP_SLAM) {
            Vec3 at = position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(1.5));
            for (int i = 0; i < 20; i++) {
                level().addParticle(ParticleTypes.CLOUD, at.x + random.nextGaussian(), at.y + 0.2, at.z + random.nextGaussian(), 0, 0.05, 0);
            }
        }
        if (moveTick >= MOVE_TICKS[move]) {
            moveTick = -1;
            move = MOVE_NONE;
        }
    }

    /** The roar: a loud cry that makes everything close by (except friends) cower for a moment. */
    private void roar() {
        playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.75F);
        AABB around = getBoundingBox().inflate(ROAR_RADIUS);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, around,
                e -> e != this && e.isAlive() && !isFriend(e) && distanceToSqr(e) < ROAR_RADIUS * ROAR_RADIUS)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2), this);
        }
    }

    /** Springs at the leap's target: far enough to land just in front of it. */
    private void takeOff() {
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        double distance = 8.0;
        if (leapAt != null) {
            distance = Math.sqrt(Math.pow(leapAt.x - getX(), 2) + Math.pow(leapAt.z - getZ(), 2));
        }
        // up at 0.6 it is in the air ~15 ticks, slowing by 9% a tick: it covers about 8.3 x its starting speed
        double speed = Mth.clamp((distance - 3.0) / 8.3, 0.3, 1.7);
        setDeltaMovement(forward.x * speed, 0.6, forward.z * speed);
        hasImpulse = true;
        playSound(SoundEvents.RAVAGER_STEP, 2.0F, 0.6F);
    }

    /**
     * Hurts everything (except friends) around a point {@code ahead} blocks in front, within {@code radius}, with the
     * given share of its attack damage and an upward knock.
     */
    private void slam(double ahead, double radius, float damageShare, double knockUp) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 center = position().add(forward.scale(ahead));
        AABB box = new AABB(center, center).inflate(radius, 2.0, radius).move(0, 1.0, 0);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * damageShare;
        boolean hit = false;
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !isFriend(e))) {
            if (target.hurt(damageSources().mobAttack(this), damage)) {
                hit = true;
                Vec3 away = target.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.8);
                target.push(away.x, knockUp, away.z);
                target.hurtMarked = true;
                chargedHit(target);
            }
        }
        playSound(hit ? SoundEvents.RAVAGER_ATTACK : SoundEvents.GENERIC_EXPLODE.value(), 1.5F, hit ? 0.7F : 1.4F);
    }

    /** Stays for its death animation (falling on its side) before it disappears. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60); // the death puff
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= MOVE_EVENT_BASE && id < MOVE_EVENT_BASE + 3) {
            move = id - MOVE_EVENT_BASE + 1;
            moveTick = 0;
            moveStartTick = tickCount;
        } else if (id == MOVE_CANCEL_EVENT) {
            move = MOVE_NONE;
            moveTick = -1;
            moveStartTick = Integer.MIN_VALUE / 2;
        } else if (id == ATTACK_EVENT) {
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
        moveTick();
        if (roarCooldown > 0) roarCooldown--;
        if (leapCooldown > 0) leapCooldown--;
        if (flipCooldown > 0) flipCooldown--;
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
