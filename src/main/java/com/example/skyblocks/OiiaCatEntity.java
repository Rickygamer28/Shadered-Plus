package com.example.skyblocks;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The OIIA cat as a companion (fed a fish, an OIIA cat block hops off it - OiiaCatBlock). It follows you about; it naps
 * curled up on or by anything warm (a hearth, lava lamps, a magma basin, a lit furnace or campfire, a bed); it plays
 * round any plushie it finds; pet it and it does its spin. Sneak-click: sit / follow. Fish heal it.
 * Drawn by client/OiiaCatEntityRenderer with the OIIA model.
 */
public class OiiaCatEntity extends TamableAnimal {
    public static final byte AWAKE = 0, NAPPING = 1, PLAYING = 2;
    /** Its entity event for being petted: the spin. */
    public static final byte SPIN_EVENT = 71;
    /** How long the spin is (the OIIA animation's loop), in ticks. */
    public static final int SPIN_TICKS = 97;
    private static final EntityDataAccessor<Byte> MOOD = SynchedEntityData.defineId(OiiaCatEntity.class, EntityDataSerializers.BYTE);

    /** Client: when it was last petted (its tickCount), for the spin. */
    public int spinStart = -1000;
    /** Client: its models (Shadered's renderables). */
    public Object clientModel, clientSpinModel;
    private int spinUntil, napCooldown = 200, playCooldown = 100;

    public OiiaCatEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 12.0).add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MOOD, AWAKE);
    }

    public byte mood() {
        return entityData.get(MOOD);
    }

    private void setMood(byte mood) {
        entityData.set(MOOD, mood);
    }

    /** Client: spinning now (petted), and how far into it (seconds). */
    public float spinSeconds(float partialTick) {
        return (tickCount - spinStart + partialTick) / 20.0F;
    }

    public boolean spinning() {
        return tickCount - spinStart < SPIN_TICKS;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new NapGoal());
        goalSelector.addGoal(4, new PlayGoal());
        goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0, 10.0F, 3.0F));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (napCooldown > 0) napCooldown--;
        if (playCooldown > 0) playCooldown--;
        if (tickCount < spinUntil) {
            getNavigation().stop(); // (it stays put for its spin)
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isTame() && isOwnedBy(player)) {
            if (isFood(stack)) {
                if (!level().isClientSide) {
                    heal(4.0F);
                    stack.consume(1, player);
                    level().broadcastEntityEvent(this, (byte) 7);
                    playSound(SoundEvents.CAT_EAT, 1.0F, 1.0F);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (stack.isEmpty()) {
                if (!level().isClientSide) {
                    if (player.isShiftKeyDown()) {
                        boolean sit = !isOrderedToSit();
                        setOrderedToSit(sit);
                        setInSittingPose(sit);
                        setMood(AWAKE);
                        jumping = false;
                        getNavigation().stop();
                        player.displayClientMessage(Component.translatable(sit ? "message.skyblocks.oiia_cat.sit" : "message.skyblocks.oiia_cat.follow"), true);
                    } else {
                        pet();
                    }
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }
        return super.mobInteract(player, hand);
    }

    /** Petted: it wakes, purrs, and does its spin. */
    private void pet() {
        setMood(AWAKE);
        spinUntil = tickCount + SPIN_TICKS;
        level().broadcastEntityEvent(this, SPIN_EVENT);
        level().broadcastEntityEvent(this, (byte) 7);
        playSound(SoundEvents.CAT_PURREOW, 1.0F, 1.0F + (random.nextFloat() - 0.5F) * 0.2F);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == SPIN_EVENT) {
            spinStart = tickCount;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && mood() == NAPPING && tickCount % 40 == 0 && random.nextInt(2) == 0) {
            // (a little "z" drifting up: a note particle, white)
            level().addParticle(ParticleTypes.WHITE_ASH, getX(), getY() + 0.5, getZ(), 0, 0.02, 0);
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.CAT_FOOD);
    }

    @Override
    public boolean canFallInLove() {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false; // (it lands on its feet)
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        if (mood() == NAPPING) {
            return SoundEvents.CAT_PURR;
        }
        return random.nextInt(4) == 0 ? SoundEvents.CAT_AMBIENT : null;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CAT_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) {
            setMood(AWAKE);
        }
        return super.hurt(source, amount);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("NapCooldown", napCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        napCooldown = tag.getInt("NapCooldown");
    }

    // ---- what it likes ----

    /** Somewhere warm to curl up: our lamps, hearth, magma basin; a lit furnace, smoker or campfire; magma; a bed. */
    static boolean warm(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof BrickHearthBlock || b instanceof LavaLampBlock || b instanceof BigLavaLampBlock || b instanceof MagmaBasinBlock
                || b instanceof BedBlock || b == Blocks.MAGMA_BLOCK) {
            return true;
        }
        if (b == Blocks.FURNACE || b == Blocks.SMOKER || b == Blocks.BLAST_FURNACE || b == Blocks.CAMPFIRE) {
            return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
        }
        return false;
    }

    static boolean plushie(BlockState state) {
        return state.getBlock() instanceof PlushieBlock;
    }

    /** Where it can stand by (or on) the block at pos: its top if there's room, else a free spot beside it. */
    @Nullable
    private BlockPos spotBy(BlockPos pos, boolean onTop) {
        Level level = level();
        if (onTop && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.above(2)).isAir()
                && level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
            return pos.above();
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos at = pos.relative(d);
            if (level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                return at;
            }
        }
        return null;
    }

    @Nullable
    private BlockPos find(java.util.function.Predicate<BlockState> what, int range) {
        BlockPos me = blockPosition(), best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(me.offset(-range, -2, -range), me.offset(range, 2, range))) {
            if (what.test(level().getBlockState(p))) {
                double d = p.distSqr(me);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    private boolean ownerNear(double range) {
        LivingEntity owner = getOwner();
        return owner == null || owner.distanceToSqr(this) < range * range;
    }

    /** Off to something warm, and a nap there, curled up (until its owner wanders off, or it's disturbed). */
    final class NapGoal extends Goal {
        private BlockPos warmAt, spot;
        private int napped, length;

        NapGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (isOrderedToSit() || napCooldown > 0 || tickCount < spinUntil || random.nextInt(60) != 0 || !ownerNear(10)) {
                return false;
            }
            warmAt = find(OiiaCatEntity::warm, 8);
            spot = warmAt == null ? null : spotBy(warmAt, !(level().getBlockState(warmAt).getBlock() instanceof LavaLampBlock));
            return spot != null;
        }

        @Override
        public void start() {
            napped = 0;
            length = 600 + random.nextInt(1200);
            getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return napped < length && !isOrderedToSit() && tickCount >= spinUntil && ownerNear(14) && warm(level().getBlockState(warmAt))
                    && (mood() == NAPPING || !getNavigation().isDone() || closeTo(spot));
        }

        private boolean closeTo(BlockPos p) {
            return distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 0.8;
        }

        @Override
        public void tick() {
            if (mood() != NAPPING) {
                if (closeTo(spot)) {
                    getNavigation().stop();
                    setMood(NAPPING);
                    Vec3 to = Vec3.atCenterOf(warmAt);
                    getLookControl().setLookAt(to.x, to.y, to.z);
                } else if (getNavigation().isDone()) {
                    getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
                }
                return;
            }
            napped++;
        }

        @Override
        public void stop() {
            setMood(AWAKE);
            napCooldown = 1200 + random.nextInt(1200);
        }
    }

    /** Off to a plushie, pouncing round it a while. */
    final class PlayGoal extends Goal {
        private BlockPos toy;
        private int played, length;

        PlayGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (isOrderedToSit() || playCooldown > 0 || tickCount < spinUntil || random.nextInt(50) != 0 || !ownerNear(10)) {
                return false;
            }
            toy = find(OiiaCatEntity::plushie, 7);
            return toy != null;
        }

        @Override
        public void start() {
            played = 0;
            length = 120 + random.nextInt(140);
            getNavigation().moveTo(toy.getX() + 0.5, toy.getY(), toy.getZ() + 0.5, 1.15);
        }

        @Override
        public boolean canContinueToUse() {
            return played < length && !isOrderedToSit() && ownerNear(14) && plushie(level().getBlockState(toy));
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(toy.getX() + 0.5, toy.getY() + 0.3, toy.getZ() + 0.5);
            double d = distanceToSqr(toy.getX() + 0.5, toy.getY(), toy.getZ() + 0.5);
            if (d > 2.6 * 2.6) {
                if (getNavigation().isDone()) {
                    getNavigation().moveTo(toy.getX() + 0.5, toy.getY(), toy.getZ() + 0.5, 1.15);
                }
                return;
            }
            setMood(PLAYING);
            played++;
            if (played % 14 == 0 && onGround()) {
                // (a pounce: a hop to a spot round the toy)
                double a = random.nextDouble() * Math.PI * 2;
                getMoveControl().setWantedPosition(toy.getX() + 0.5 + Math.cos(a) * 1.1, toy.getY(), toy.getZ() + 0.5 + Math.sin(a) * 1.1, 1.3);
                getJumpControl().jump();
            }
            if (random.nextInt(70) == 0) {
                playSound(SoundEvents.CAT_AMBIENT, 0.5F, 1.3F);
            }
            if (random.nextInt(160) == 0) {
                spinUntil = tickCount + SPIN_TICKS / 3;
                level().broadcastEntityEvent(OiiaCatEntity.this, SPIN_EVENT); // (a happy little spin)
            }
        }

        @Override
        public void stop() {
            setMood(AWAKE);
            playCooldown = 600 + random.nextInt(900);
        }
    }
}
