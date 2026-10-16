package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
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
    /** How much bigger the Alpha bosses are. */
    public static final float ALPHA_SIZE = 3.0F;
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
    /**
     * Leap: when its paws leave the ground and come down in the clip (the jump is timed to land then), and for how many
     * ticks after take-off it can still bend its path toward a target that dodges.
     */
    private static final int LEAP_TAKEOFF = 5, LEAP_LAND = 22, LEAP_STEER = 6;
    private static final int LEAP_AIR = LEAP_LAND - LEAP_TAKEOFF;
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
    /** Server: how far up or down from take-off the leap is meant to land. */
    private double leapRise;

    /** Client only: the renderer's per-mob state (model copy and animation player). */
    public Object clientState;

    public ZinogreEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.moveControl = new ZinogreMoveControl(this);
        this.xpReward = type == ZinogreMod.ALPHA_ZINOGRE.get() || type == ZinogreMod.ALPHA_STYGIAN_ZINOGRE.get() ? 500 : 40;
    }

    /** Walking speed (an Alpha's a little faster, on top of its longer stride). */
    private static final double SPEED = 0.35, ALPHA_SPEED = 0.38;

    public static AttributeSupplier.Builder createAttributes(boolean stygian) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, stygian ? 260.0 : 200.0)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
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
        if (isAlpha()) {
            // a boss goes for any player it can see
            targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }
    }

    public boolean isStygian() {
        return getType() == ZinogreMod.STYGIAN_ZINOGRE.get() || getType() == ZinogreMod.ALPHA_STYGIAN_ZINOGRE.get();
    }

    /** An Alpha (boss): three times as big, much tougher, can't be tamed, has a boss bar and drops an egg. */
    public boolean isAlpha() {
        return getType() == ZinogreMod.ALPHA_ZINOGRE.get() || getType() == ZinogreMod.ALPHA_STYGIAN_ZINOGRE.get();
    }

    /** How big it is compared to a normal Zinogre (for its reach, hit areas and so on). */
    public float sizeScale() {
        return isAlpha() ? ALPHA_SIZE : 1.0F;
    }

    public static AttributeSupplier.Builder createAlphaAttributes(boolean stygian) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, stygian ? 1300.0 : 1000.0)
                .add(Attributes.MOVEMENT_SPEED, ALPHA_SPEED)
                .add(Attributes.ATTACK_DAMAGE, stygian ? 40.0 : 31.0)
                .add(Attributes.ATTACK_KNOCKBACK, 3.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, stygian ? 18.0 : 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.STEP_HEIGHT, 3.0);
    }

    // ---------------------------------------------------------------- boss bar (Alphas)

    @Nullable
    private net.minecraft.server.level.ServerBossEvent bossEvent;

    @Nullable
    private net.minecraft.server.level.ServerBossEvent bossEvent() {
        if (bossEvent == null && isAlpha() && !level().isClientSide) {
            bossEvent = new net.minecraft.server.level.ServerBossEvent(bossName(),
                    isStygian() ? net.minecraft.world.BossEvent.BossBarColor.RED : net.minecraft.world.BossEvent.BossBarColor.BLUE,
                    net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10);
        }
        return bossEvent;
    }

    /** The boss bar's title: its name, wrapped in a key the client recognises to draw our own bar (ZinogreBossBar). */
    private net.minecraft.network.chat.Component bossName() {
        return net.minecraft.network.chat.Component.translatable(isStygian() ? "boss.zinogre.alpha_stygian" : "boss.zinogre.alpha", getDisplayName());
    }

    @Override
    public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (bossEvent() != null) {
            bossEvent().addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (bossEvent() != null) {
            bossEvent().removePlayer(player);
        }
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        if (bossEvent() != null) {
            bossEvent().setName(bossName());
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (bossEvent() != null) {
            bossEvent().setProgress(getHealth() / getMaxHealth());
        }
    }

    /** Alphas don't take fall damage (they leap a long way). */
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !isAlpha() && move != MOVE_LEAP && super.causeFallDamage(distance, multiplier, source);
    }

    /**
     * A thunder wolf: lightning doesn't hurt it, it charges it up (the one that hatches it from its egg, too).
     */
    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        if (!isCharged() && isAlive()) {
            becomeCharged();
        }
    }

    /** Server: just hatched from its egg. */
    void hatched() {
        chargeCooldown = 100;
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
        playSound(ZinogreSounds.GROWL.get(), 2.0F, getVoicePitch());
        playSound(ZinogreSounds.CHARGE_UP.get(), 2.0F, isAlpha() ? 0.8F : 1.0F);
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
        modifier(Attributes.ATTACK_DAMAGE, CHARGED_DAMAGE, isTame() ? 0.25 : 0.5); // pets: +25%
        modifier(Attributes.MOVEMENT_SPEED, CHARGED_SPEED, 0.15);
        playSound(ZinogreSounds.HOWL.get(), 3.0F, getVoicePitch());
        playSound(ZinogreSounds.ZAP.get(), 2.5F, isAlpha() ? 0.75F : 1.0F);
        playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1.6F);
        if (level() instanceof ServerLevel server) {
            float k = sizeScale();
            server.sendParticles(sparkParticle(), getX(), getY() + 1.4 * k, getZ(), (int) (120 * k), 1.4 * k, 0.9 * k, 1.4 * k, 0.2);
        }
    }

    private void endCharge() {
        entityData.set(CHARGED, false);
        chargedLeft = 0;
        chargeCooldown = CHARGE_COOLDOWN;
        removeModifier(Attributes.ATTACK_DAMAGE, CHARGED_DAMAGE);
        removeModifier(Attributes.MOVEMENT_SPEED, CHARGED_SPEED);
        playSound(ZinogreSounds.DISCHARGE.get(), 1.5F, isAlpha() ? 0.8F : 1.0F);
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
            float k = sizeScale();
            for (int n = 0; n < (isAlpha() ? 4 : 1); n++) {
                if (isCharged() && random.nextInt(2) == 0) {
                    Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot);
                    double along = (random.nextDouble() - 0.4) * 4.0 * k;
                    level().addParticle(sparkParticle(), getX() + forward.x * along + (random.nextDouble() - 0.5) * 1.6 * k,
                            getY() + (1.2 + random.nextDouble() * 1.2) * k, getZ() + forward.z * along + (random.nextDouble() - 0.5) * 1.6 * k,
                            (random.nextDouble() - 0.5) * 0.2, 0.1, (random.nextDouble() - 0.5) * 0.2);
                }
            }
            return;
        }
        if (chargeCooldown > 0) {
            chargeCooldown--;
        }
        if (isCharged()) {
            if (--chargedLeft <= 0 || isOrderedToSit()) {
                endCharge();
            } else if (random.nextInt(50) == 0) {
                playSound(ZinogreSounds.CRACKLE.get(), 1.2F, 0.85F + random.nextFloat() * 0.3F);
            }
        }
    }

    /** Charged hits sometimes call down lightning on the target (visual only). */
    private void chargedHit(Entity target) {
        if (isCharged()) {
            level().playSound(null, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), ZinogreSounds.ZAP.get(),
                    getSoundSource(), 1.5F, 0.9F + random.nextFloat() * 0.2F);
        }
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
        at = at.scale(sizeScale()).add(position());
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0.3);
            server.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.1);
        }
        level().playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BREAK, getSoundSource(), 2.0F, 0.6F);
        playSound(ZinogreSounds.PART_BREAK.get(), 1.8F, getVoicePitch());
    }

    /** Like mob heads: killed by a charged creeper's explosion, it drops its head (one head per creeper). */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (isAlpha()) {
            spawnAtLocation(new ItemStack(isStygian() ? ZinogreMod.STYGIAN_EGG_ITEM.get() : ZinogreMod.EGG_ITEM.get()));
        }
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
        // Zinogres saved before a speed change keep up with the new speed (the saved base value would win otherwise)
        net.minecraft.world.entity.ai.attributes.AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(isAlpha() ? ALPHA_SPEED : SPEED);
        }
        entityData.set(PARTS, tag.getInt("Parts"));
        for (int group : BREAKABLE) {
            partDamage[group] = tag.getFloat("PartDamage" + group);
        }
        if (isTame()) {
            petBalance(); // (pets tamed before the balance came in get it now)
        }
    }

    // ---------------------------------------------------------------- taming and sitting

    private static final ResourceLocation PET_HEALTH = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "pet_health");
    private static final ResourceLocation PET_DAMAGE = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "pet_damage");
    private static final ResourceLocation PET_ARMOR = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "pet_armor");

    @Override
    protected void applyTamingSideEffects() {
        super.applyTamingSideEffects();
        petBalance();
    }

    /**
     * A pet Zinogre is a strong companion, not a walking boss: half the wild one's health and armor and 65% of its attack
     * (Zinogre 100 health / 9.1 attack, Stygian 130 / 11.7; an iron golem has 100 health and hits for 7-21).
     */
    private void petBalance() {
        double k = isTame() ? -0.5 : 0.0;
        petModifier(Attributes.MAX_HEALTH, PET_HEALTH, k);
        petModifier(Attributes.ATTACK_DAMAGE, PET_DAMAGE, isTame() ? -0.35 : 0.0);
        petModifier(Attributes.ARMOR, PET_ARMOR, k);
        if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    private void petModifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount != 0) {
            instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    /** Raw or cooked meat tames it and heals it. */
    @Override
    public boolean isFood(ItemStack stack) {
        return !isAlpha() && stack.is(ItemTags.MEAT);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isAlpha()) {
            return InteractionResult.PASS; // a boss: can't be fed or tamed
        }
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
            playSound(ZinogreSounds.GROWL.get(), 2.0F, getVoicePitch());
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
        if (server && move == MOVE_LEAP && moveTick > LEAP_TAKEOFF && moveTick <= LEAP_TAKEOFF + LEAP_STEER) {
            steerLeap();
        }
        if (server && move == MOVE_LEAP && !leapLanded && moveTick > LEAP_TAKEOFF + 3 && (onGround() || moveTick >= LEAP_LAND + 6)) {
            leapLanded = true;
            land();
        }
        if (level().isClientSide && move == MOVE_LEAP && isCharged() && moveTick > LEAP_TAKEOFF && !onGround()) {
            float k = sizeScale(); // a trail of sparks while a charged Zinogre is in the air
            for (int i = 0; i < 4 * k; i++) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(0.8), getRandomY(), getRandomZ(0.8),
                        random.nextGaussian() * 0.1, random.nextGaussian() * 0.1, random.nextGaussian() * 0.1);
            }
        }
        if (server && move == MOVE_FLIP && moveTick == FLIP_SLAM) {
            slam(1.0, 3.6, 1.25F, 0.55);
        }
        if (move == MOVE_FLIP && level().isClientSide && moveTick == FLIP_SLAM) {
            float k = sizeScale();
            Vec3 at = position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(1.5 * k));
            for (int i = 0; i < 20 * k; i++) {
                level().addParticle(ParticleTypes.CLOUD, at.x + random.nextGaussian() * k, at.y + 0.2, at.z + random.nextGaussian() * k, 0, 0.05, 0);
            }
        }
        if (moveTick >= MOVE_TICKS[move]) {
            moveTick = -1;
            move = MOVE_NONE;
        }
    }

    /** The roar: a loud cry that makes everything close by (except friends) cower for a moment. */
    private void roar() {
        playSound(ZinogreSounds.ROAR.get(), isAlpha() ? 6.0F : 3.0F, isAlpha() ? 0.75F : 1.0F);
        if (isCharged()) {
            playSound(ZinogreSounds.CRACKLE.get(), 2.0F, 0.8F);
        }
        double radius = ROAR_RADIUS * (isAlpha() ? 2.0 : 1.0);
        AABB around = getBoundingBox().inflate(radius);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, around,
                e -> e != this && e.isAlive() && !isFriend(e) && distanceToSqr(e) < radius * radius
                        && !(isTame() && e instanceof Player))) { // a pet's roar only frightens mobs
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, isAlpha() ? 80 : 50, 2), this);
        }
    }

    /**
     * Springs at the leap's target: where it will be when the leap comes down (a running target is led a little), so
     * it lands with its front paws on it. The jump is worked out from the game's own physics (gravity, air drag) to come
     * down exactly when the clip lands, at the target's height.
     */
    private void takeOff() {
        LivingEntity target = getTarget();
        Vec3 aim = leapAt;
        if (target != null && target.isAlive()) {
            Vec3 v = target.getDeltaMovement();
            aim = target.position().add(v.x * LEAP_AIR * 0.6, 0.0, v.z * LEAP_AIR * 0.6);
        }
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        double run = 8.0 * sizeScale();
        leapRise = 0.0;
        if (aim != null) {
            Vec3 flat = new Vec3(aim.x - getX(), 0.0, aim.z - getZ());
            if (flat.lengthSqr() > 0.01) {
                forward = flat.normalize();
            }
            run = Mth.clamp(flat.length() - landingStop(), 1.5, isAlpha() ? 32.0 : 18.0);
            leapRise = Mth.clamp(aim.y - getY(), -6.0, 5.0);
        }
        float yaw = (float) (Mth.atan2(forward.z, forward.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        double h = run / airDragSum(LEAP_AIR);
        setDeltaMovement(forward.x * h, jumpSpeed(leapRise, LEAP_AIR), forward.z * h);
        hasImpulse = true;
        playSound(ZinogreSounds.LEAP.get(), 2.0F, isAlpha() ? 0.75F : 1.0F);
        if (level() instanceof ServerLevel server) {
            groundBurst(server, position(), 0.6 * sizeScale(), 14, 0.15); // kicks up the ground it springs from
        }
    }

    /** How far short of its target the leap stops: the target ends up under its front paws, in the landing's reach. */
    private double landingStop() {
        return getBbWidth() * 0.5 + 0.3 * sizeScale();
    }

    /** Early in the jump it can still bend its path a little toward where its target has gone (late dodges work). */
    private void steerLeap() {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        int left = Math.max(1, LEAP_TAKEOFF + LEAP_AIR - moveTick);
        Vec3 flat = new Vec3(target.getX() - getX(), 0.0, target.getZ() - getZ());
        double run = Math.max(0.0, flat.length() - landingStop());
        Vec3 want = flat.lengthSqr() > 0.01 ? flat.normalize().scale(run / airDragSum(left)) : Vec3.ZERO;
        Vec3 v = getDeltaMovement();
        double cap = (isAlpha() ? 0.25 : 0.12); // most it bends per tick (blocks per tick)
        Vec3 change = new Vec3(want.x - v.x, 0.0, want.z - v.z).scale(0.3);
        if (change.length() > cap) {
            change = change.normalize().scale(cap);
        }
        setDeltaMovement(v.add(change));
        hurtMarked = true;
    }

    /** Sum of 0.91^i over the ticks in the air: horizontal distance covered per unit of starting speed (air drag 0.91). */
    private static double airDragSum(int ticks) {
        double sum = 0.0, f = 1.0;
        for (int i = 0; i < ticks; i++) {
            sum += f;
            f *= 0.91;
        }
        return sum;
    }

    /** The upward speed that brings it down {@code rise} blocks higher after {@code ticks} ticks (gravity 0.08, drag 0.98). */
    private static double jumpSpeed(double rise, int ticks) {
        // height after n ticks = v0 * A - B (each tick: moves, then v = (v - 0.08) * 0.98)
        double a = 0.0, b = 0.0, decay = 1.0, fall = 0.0;
        for (int i = 0; i < ticks; i++) {
            a += decay;
            b += fall;
            decay *= 0.98;
            fall = (fall + 0.08) * 0.98;
        }
        return (rise + b) / a;
    }

    /**
     * The landing: a shockwave around its front paws, hardest in the middle, throwing everything back and up a little;
     * a ring of dust, and for a charged Zinogre sparks and lightning striking around it. An Alpha that missed often leaps
     * again straight away.
     */
    private void land() {
        float k = sizeScale();
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 center = position().add(forward.scale(getBbWidth() * 0.35));
        double radius = getBbWidth() * 0.5 + 2.0 * k;
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x * 0.15, v.y, v.z * 0.15); // digs in rather than sliding on
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.1F;
        boolean hit = false;
        AABB box = new AABB(center, center).inflate(radius, 1.5 * k, radius).move(0, 0.5 * k, 0);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !isFriend(e))) {
            double d = Math.sqrt(target.distanceToSqr(center.x, target.getY(), center.z));
            if (d > radius + target.getBbWidth() * 0.5) {
                continue;
            }
            float share = (float) (1.0 - 0.45 * Mth.clamp(d / radius, 0.0, 1.0));
            if (target.hurt(damageSources().mobAttack(this), damage * share)) {
                hit = true;
                Vec3 away = new Vec3(target.getX() - getX(), 0.0, target.getZ() - getZ());
                away = away.lengthSqr() > 0.01 ? away.normalize() : forward;
                double push = (0.6 + 0.6 * share) * Math.sqrt(k);
                target.push(away.x * push, 0.35 + 0.15 * share, away.z * push);
                target.hurtMarked = true;
                chargedHit(target);
            }
        }
        playSound(ZinogreSounds.SLAM.get(), isAlpha() ? 3.5F : 2.0F, isAlpha() ? 0.75F : 1.0F);
        if (hit) {
            playSound(ZinogreSounds.BITE.get(), 1.5F, getVoicePitch());
        }
        if (level() instanceof ServerLevel server) {
            groundBurst(server, center, radius * 0.35, 40, 0.35 * Math.sqrt(k));
            server.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.3, center.z, (int) (2 * k), radius * 0.3, 0.1, radius * 0.3, 0.0);
            if (isCharged()) {
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 0.5, center.z, (int) (60 * k), radius * 0.5, 0.4, radius * 0.5, 0.6);
                playSound(ZinogreSounds.ZAP.get(), 2.5F, isAlpha() ? 0.7F : 0.9F);
                for (int i = 0; i < 3; i++) { // lightning around the landing (for show, the shockwave did the damage)
                    double a = getYRot() * Mth.DEG_TO_RAD + i * Mth.TWO_PI / 3 + random.nextDouble() * 0.6;
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
                    if (bolt != null) {
                        bolt.moveTo(center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius);
                        bolt.setVisualOnly(true);
                        server.addFreshEntity(bolt);
                    }
                }
            }
        }
        LivingEntity target = getTarget();
        if (isAlpha() && !hit && target != null && target.isAlive() && distanceTo(target) > 10.0 && random.nextBoolean()) {
            leapCooldown = 12; // chases with another leap
        }
    }

    /** A ring of the ground's own dust blown outwards, plus a puff of cloud (server: sent to everyone near). */
    private void groundBurst(ServerLevel server, Vec3 at, double ring, int count, double speed) {
        BlockState ground = level().getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
        ParticleOptions dust = ground.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, ground);
        for (int i = 0; i < count; i++) {
            double a = i * Mth.TWO_PI / count + random.nextDouble() * 0.2;
            double dx = Math.cos(a), dz = Math.sin(a);
            // count 0: the offsets are the direction the particle flies, scaled by speed
            server.sendParticles(dust, at.x + dx * ring, at.y + 0.1, at.z + dz * ring, 0, dx, 0.25, dz, speed * (0.8 + random.nextDouble() * 0.4));
        }
        server.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, count / 3, ring, 0.05, ring, 0.04);
    }

    private void slam(double ahead, double radius, float damageShare, double knockUp) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        float k = sizeScale();
        Vec3 center = position().add(forward.scale(ahead * k));
        AABB box = new AABB(center, center).inflate(radius * k, 2.0 * k, radius * k).move(0, k, 0);
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
        playSound(ZinogreSounds.SLAM.get(), isAlpha() ? 3.0F : 1.8F, isAlpha() ? 0.75F : 1.0F);
        if (hit) {
            playSound(ZinogreSounds.BITE.get(), 1.5F, getVoicePitch());
        }
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
        float k = sizeScale();
        Vec3 center = position().add(forward.scale(3.0 * k)).add(0.0, 1.2 * k, 0.0);
        AABB box = new AABB(center, center).inflate(1.8 * k, 1.4 * k, 1.8 * k);
        boolean hit = false;
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !isFriend(e))) {
            if (doHurtTarget(target)) {
                hit = true;
                chargedHit(target);
            }
        }
        playSound(hit ? ZinogreSounds.BITE.get() : ZinogreSounds.SWING.get(), 1.5F, isAlpha() ? 0.75F : 0.9F + random.nextFloat() * 0.2F);
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
        return getBoundingBox().inflate(3.0 * sizeScale(), 0.5 * sizeScale(), 3.0 * sizeScale());
    }

    /** An Alpha's voice is deeper. */
    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * (isAlpha() ? 0.75F : 1.0F);
    }

    @Override
    public void playAmbientSound() {
        super.playAmbientSound();
        if (isCharged() && !isInSittingPose()) {
            playSound(ZinogreSounds.CRACKLE.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isInSittingPose() ? null : ZinogreSounds.AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ZinogreSounds.HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ZinogreSounds.DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // no footstep sounds
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }
}
