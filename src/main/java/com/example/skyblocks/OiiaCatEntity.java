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
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import org.jetbrains.annotations.Nullable;

/**
 * The OIIA cat as a companion (fed a fish, an OIIA cat block hops off it - OiiaCatBlock). It follows you about; it naps
 * in its cat bed (or curled up on or by anything warm: a hearth, lava lamps, a magma basin, a lit furnace or campfire, a
 * bed); it eats from its bowl when it's hungry; it scratches its post, climbs its cat tree, squeezes into boxes, plays
 * round plushies, chases yarn balls, stalks a feather wand and a laser dot, and walks through cat flaps. All that keeps
 * it happy (petting it tells you how happy), and a happy cat now and then leaves you a little gift. Pet it and it does
 * its spin. Sneak-click: sit / follow. Fish heal it; a dye colours its collar.
 * Drawn by client/OiiaCatEntityRenderer with the OIIA model.
 */
public class OiiaCatEntity extends TamableAnimal {
    public static final byte AWAKE = 0, NAPPING = 1, PLAYING = 2, SCRATCHING = 3, EATING = 4, BOXED = 5, PERCHED = 6;
    /** Its entity event for being petted: the spin. */
    public static final byte SPIN_EVENT = 71;
    /** How long the spin is (the OIIA animation's loop), in ticks. */
    public static final int SPIN_TICKS = 97;
    private static final EntityDataAccessor<Byte> MOOD = SynchedEntityData.defineId(OiiaCatEntity.class, EntityDataSerializers.BYTE);
    /** Its collar's dye colour (DyeColor id). */
    private static final EntityDataAccessor<Byte> COLLAR = SynchedEntityData.defineId(OiiaCatEntity.class, EntityDataSerializers.BYTE);

    /** Client: when it was last petted (its tickCount), for the spin. */
    public int spinStart = -1000;
    /** Client: its models (Shadered's renderables). */
    public Object clientModel, clientSpinModel, clientParts;
    private int spinUntil, napCooldown = 200, playCooldown = 100;
    private int scratchCooldown = 600, boxCooldown = 800, perchCooldown = 900, yarnCooldown, petCooldown, giftCooldown = 6000;
    /** Ticks till it's hungry again. */
    private int hunger = 6000;
    /** How happy it is, 0 .. 100. */
    private int happiness = 50;

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
        builder.define(COLLAR, (byte) DyeColor.RED.getId());
    }

    public byte mood() {
        return entityData.get(MOOD);
    }

    private void setMood(byte mood) {
        entityData.set(MOOD, mood);
    }

    public DyeColor collar() {
        return DyeColor.byId(entityData.get(COLLAR));
    }

    /** Happier (or less happy) by this much; a big lift shows (hearts). */
    void cheer(int by) {
        int before = happiness;
        happiness = Mth.clamp(happiness + by, 0, 100);
        if (by >= 5 && happiness > before) {
            level().broadcastEntityEvent(this, (byte) 7);
        }
    }

    public int happiness() {
        return happiness;
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
        goalSelector.addGoal(3, new LaserGoal());
        goalSelector.addGoal(3, new WandGoal());
        goalSelector.addGoal(4, new EatGoal());
        goalSelector.addGoal(4, new YarnGoal());
        goalSelector.addGoal(5, new NapGoal());
        goalSelector.addGoal(5, new ScratchGoal());
        goalSelector.addGoal(5, new BoxGoal());
        goalSelector.addGoal(5, new PerchGoal());
        goalSelector.addGoal(5, new PlayGoal());
        goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 3.0F));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (napCooldown > 0) napCooldown--;
        if (playCooldown > 0) playCooldown--;
        if (scratchCooldown > 0) scratchCooldown--;
        if (boxCooldown > 0) boxCooldown--;
        if (perchCooldown > 0) perchCooldown--;
        if (yarnCooldown > 0) yarnCooldown--;
        if (petCooldown > 0) petCooldown--;
        if (giftCooldown > 0) giftCooldown--;
        if (hunger > 0) hunger--;
        if (isTame() && tickCount % 1200 == 0) {
            happiness = Math.max(0, happiness - 1); // (it wants things to do)
        }
        if (happiness >= 70 && tickCount % 600 == 0 && getHealth() < getMaxHealth()) {
            heal(1.0F);
        }
        if (happiness >= 85 && mood() == AWAKE && tickCount >= spinUntil && random.nextInt(2400) == 0) {
            spinUntil = tickCount + SPIN_TICKS;
            level().broadcastEntityEvent(this, SPIN_EVENT); // (a happy spin, all by itself)
        }
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
            if (stack.getItem() instanceof DyeItem dye && dye.getDyeColor() != collar()) {
                if (!level().isClientSide) {
                    entityData.set(COLLAR, (byte) dye.getDyeColor().getId());
                    stack.consume(1, player);
                    playSound(SoundEvents.DYE_USE, 1.0F, 1.0F);
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
                        pet(player);
                    }
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }
        return super.mobInteract(player, hand);
    }

    /** Petted: it wakes, purrs, and does its spin (grumpy, it may just hiss); you see how happy it is. */
    private void pet(Player player) {
        if (petCooldown == 0) {
            cheer(3);
            petCooldown = 400;
        }
        String how = happiness < 20 ? "grumpy" : happiness < 45 ? "okay" : happiness < 70 ? "happy" : happiness < 90 ? "very_happy" : "overjoyed";
        player.displayClientMessage(Component.translatable("message.skyblocks.oiia_cat.mood", getDisplayName(),
                Component.translatable("message.skyblocks.oiia_cat.mood." + how)), true);
        if (happiness < 20 && random.nextBoolean()) {
            playSound(SoundEvents.CAT_HISS, 0.8F, 1.0F);
            return;
        }
        if (mood() == NAPPING || mood() == BOXED || mood() == PERCHED) {
            playSound(SoundEvents.CAT_PURR, 1.0F, 1.0F); // (stays put, purring)
            level().broadcastEntityEvent(this, (byte) 7);
            return;
        }
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
        tag.putInt("Happiness", happiness);
        tag.putInt("Hunger", hunger);
        tag.putInt("GiftCooldown", giftCooldown);
        tag.putByte("CollarColor", entityData.get(COLLAR));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        napCooldown = tag.getInt("NapCooldown");
        if (tag.contains("Happiness")) {
            happiness = tag.getInt("Happiness");
            hunger = tag.getInt("Hunger");
            giftCooldown = tag.getInt("GiftCooldown");
        }
        if (tag.contains("CollarColor")) {
            entityData.set(COLLAR, tag.getByte("CollarColor"));
        }
        if (tag.contains("Mood")) {
            setMood(tag.getByte("Mood")); // (a pose to start in: the render test's cats)
        }
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

    static boolean bed(BlockState state) {
        return state.getBlock() instanceof CatBedBlock;
    }

    static boolean cozy(BlockState state) {
        return bed(state) || warm(state);
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
        private boolean inBed;

        NapGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (isOrderedToSit() || napCooldown > 0 || tickCount < spinUntil || random.nextInt(60) != 0 || !ownerNear(10)) {
                return false;
            }
            warmAt = find(OiiaCatEntity::bed, 12);
            inBed = warmAt != null && free(warmAt);
            if (inBed) {
                spot = warmAt;
                return true;
            }
            warmAt = find(OiiaCatEntity::warm, 8);
            spot = warmAt == null ? null : spotBy(warmAt, !(level().getBlockState(warmAt).getBlock() instanceof LavaLampBlock));
            return spot != null;
        }

        @Override
        public void start() {
            napped = 0;
            length = inBed ? 1200 + random.nextInt(1800) : 600 + random.nextInt(1200);
            getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return napped < length && !isOrderedToSit() && tickCount >= spinUntil && ownerNear(14) && cozy(level().getBlockState(warmAt))
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
            if (napped > 400) {
                cheer(inBed ? 10 : 4);
                if (inBed && happiness >= 60 && giftCooldown == 0 && random.nextBoolean()) {
                    gift(warmAt);
                }
            }
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
            if (played > 80) {
                cheer(4);
            }
        }
    }

    // ---- getting about: cat flaps; hopping into boxes and up trees ----

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level) {
            @Override
            protected PathFinder createPathFinder(int maxVisitedNodes) {
                nodeEvaluator = new FlapWalker();
                nodeEvaluator.setCanPassDoors(true);
                return new PathFinder(nodeEvaluator, maxVisitedNodes);
            }
        };
    }

    /** Finds its way as any mob walking, but through cat flaps. */
    static final class FlapWalker extends WalkNodeEvaluator {
        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            if (context.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof CatFlapBlock) {
                return PathType.OPEN;
            }
            return super.getPathType(context, x, y, z);
        }
    }

    /** No other cat's on (or in) the block at pos. */
    private boolean free(BlockPos pos) {
        return level().getEntitiesOfClass(OiiaCatEntity.class, new AABB(pos).inflate(0.1, 1.5, 0.1), c -> c != this).isEmpty();
    }

    /** A quick hop to there. */
    private void hop(Vec3 to) {
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        teleportTo(to.x, to.y, to.z);
    }

    /** Hops down from (or out of) the block at pos, to a free spot beside it. */
    private void hopOff(BlockPos pos) {
        BlockPos out = spotBy(pos, false);
        if (out != null) {
            hop(new Vec3(out.getX() + 0.5, out.getY(), out.getZ() + 0.5));
        }
    }

    /** Close enough (on the level, give or take a step) to a point. */
    private boolean near(Vec3 p, double d) {
        return distanceToSqr(p.x, getY(), p.z) < d * d && Math.abs(getY() - p.y) < 1.2;
    }

    private void walkTo(Vec3 p, double speed, int every) {
        if (getNavigation().isDone() || tickCount % every == 0) {
            getNavigation().moveTo(p.x, p.y, p.z, speed);
        }
    }

    private static Vec3 centre(BlockPos p) {
        return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    /** A little present by its bed: what a cat might bring in. */
    private void gift(BlockPos bed) {
        giftCooldown = 12000 + random.nextInt(12000);
        net.minecraft.world.item.Item[] gifts = {Items.FEATHER, Items.STRING, Items.STRING, Items.COD, Items.RABBIT_HIDE, Items.KELP,
                Items.POPPY, Items.DANDELION, Items.BONE, Items.PHANTOM_MEMBRANE};
        ItemStack gift = new ItemStack(gifts[random.nextInt(random.nextInt(20) == 0 ? gifts.length : gifts.length - 1)]);
        BlockPos at = spotBy(bed, false);
        Vec3 drop = at != null ? centre(at).add(0, 0.2, 0) : position().add(0, 0.3, 0);
        level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level(), drop.x, drop.y, drop.z, gift, 0, 0.1, 0));
        playSound(SoundEvents.CAT_PURREOW, 0.8F, 1.2F);
        if (getOwner() instanceof Player owner && owner.distanceToSqr(this) < 24 * 24) {
            owner.displayClientMessage(Component.translatable("message.skyblocks.oiia_cat.gift", getDisplayName()), true);
        }
    }

    /** Hungry or hurt: off to a bowl with food in it, and eats a serving. */
    final class EatGoal extends Goal {
        private BlockPos bowl, spot;
        private int eaten;

        EatGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        private boolean full(BlockPos p) {
            BlockState st = level().getBlockState(p);
            return st.getBlock() instanceof CatBowlBlock && st.getValue(CatBowlBlock.FOOD) > 0;
        }

        @Override
        public boolean canUse() {
            if (isOrderedToSit() || tickCount < spinUntil || (hunger > 0 && getHealth() >= getMaxHealth() - 1) || random.nextInt(20) != 0) {
                return false;
            }
            bowl = find(st -> st.getBlock() instanceof CatBowlBlock && st.getValue(CatBowlBlock.FOOD) > 0, 12);
            spot = bowl == null ? null : spotBy(bowl, false);
            return spot != null;
        }

        @Override
        public void start() {
            eaten = 0;
            getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.1);
        }

        @Override
        public boolean canContinueToUse() {
            return eaten < 70 && !isOrderedToSit() && full(bowl);
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(bowl.getX() + 0.5, bowl.getY(), bowl.getZ() + 0.5);
            if (!near(centre(spot), 0.6)) {
                walkTo(centre(spot), 1.1, 20);
                return;
            }
            getNavigation().stop();
            setMood(EATING);
            eaten++;
            if (eaten % 12 == 0 && level() instanceof ServerLevel server) {
                playSound(SoundEvents.CAT_EAT, 0.7F, 1.0F + (random.nextFloat() - 0.5F) * 0.2F);
                CatBowlBlock.crumbs(server, bowl);
            }
            if (eaten == 70 && level() instanceof ServerLevel server) {
                CatBowlBlock.eaten(server, bowl);
                heal(4.0F);
                hunger = 6000 + random.nextInt(6000);
                cheer(8);
            }
        }

        @Override
        public void stop() {
            setMood(AWAKE);
        }
    }

    /** Over to a scratching post (or a cat tree's post): up on its back legs, a good scratch. */
    final class ScratchGoal extends Goal {
        private BlockPos post, spot;
        private int scratched, length;

        ScratchGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (isOrderedToSit() || scratchCooldown > 0 || tickCount < spinUntil || random.nextInt(60) != 0 || !ownerNear(12)) {
                return false;
            }
            post = find(st -> st.getBlock() instanceof ScratchingPostBlock
                    || st.getBlock() instanceof CatTreeBlock && st.getValue(CatTreeBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER, 10);
            spot = post == null ? null : spotBy(post, false);
            return spot != null;
        }

        @Override
        public void start() {
            scratched = 0;
            length = 60 + random.nextInt(60);
            getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            Block b = level().getBlockState(post).getBlock();
            return scratched < length && !isOrderedToSit() && (b instanceof ScratchingPostBlock || b instanceof CatTreeBlock);
        }

        @Override
        public void tick() {
            Vec3 c = centre(post);
            getLookControl().setLookAt(c.x, c.y + 0.6, c.z);
            if (mood() != SCRATCHING) {
                if (!near(centre(spot), 0.6)) {
                    walkTo(centre(spot), 1.0, 20);
                    return;
                }
                getNavigation().stop();
                setMood(SCRATCHING);
            }
            // (up close to the post, facing it)
            Vec3 to = position().subtract(c).multiply(1, 0, 1);
            if (to.lengthSqr() > 1.0E-4) {
                Vec3 at = c.add(to.normalize().scale(0.62));
                getMoveControl().setWantedPosition(at.x, getY(), at.z, 0.4);
                float yaw = (float) (Mth.atan2(c.z - getZ(), c.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
                setYRot(yaw);
                yBodyRot = yaw;
                yHeadRot = yaw;
            }
            scratched++;
            if (scratched % 8 == 0) {
                playSound(SoundEvents.WOOL_HIT, 0.5F, 0.8F + random.nextFloat() * 0.4F);
            }
        }

        @Override
        public void stop() {
            setMood(AWAKE);
            scratchCooldown = 2400 + random.nextInt(2400);
            if (scratched > 40) {
                ScratchingPostBlock.scratched(level(), post);
                cheer(6);
            }
        }
    }

    /** If it fits, it sits: into a cardboard box, a while, only its head showing. */
    final class BoxGoal extends Goal {
        private BlockPos box, spot;
        private int sat, length;

        BoxGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (isOrderedToSit() || boxCooldown > 0 || tickCount < spinUntil || random.nextInt(60) != 0 || !ownerNear(10)) {
                return false;
            }
            box = find(st -> st.getBlock() instanceof CardboardBoxBlock, 10);
            if (box == null || !free(box)) {
                return false;
            }
            spot = spotBy(box, false);
            return spot != null;
        }

        @Override
        public void start() {
            sat = 0;
            length = 600 + random.nextInt(1200);
            getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return sat < length && !isOrderedToSit() && ownerNear(16) && level().getBlockState(box).getBlock() instanceof CardboardBoxBlock;
        }

        @Override
        public void tick() {
            if (mood() != BOXED) {
                if (!near(centre(spot), 0.7)) {
                    walkTo(centre(spot), 1.0, 20);
                    return;
                }
                hop(CardboardBoxBlock.inside(box));
                setMood(BOXED);
                playSound(SoundEvents.SCAFFOLDING_PLACE, 0.5F, 1.2F);
            }
            getNavigation().stop();
            sat++;
            if (getOwner() instanceof LivingEntity owner) {
                getLookControl().setLookAt(owner, 20.0F, 20.0F);
            }
        }

        @Override
        public void stop() {
            if (mood() == BOXED) {
                hopOff(box);
                if (sat > 200) {
                    cheer(5);
                }
            }
            setMood(AWAKE);
            boxCooldown = 2400 + random.nextInt(3600);
        }
    }

    /** Up a cat tree, to sit on top and keep an eye on you. */
    final class PerchGoal extends Goal {
        private BlockPos tree, spot;
        private int sat, length;

        PerchGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (isOrderedToSit() || perchCooldown > 0 || tickCount < spinUntil || random.nextInt(60) != 0 || !ownerNear(12)) {
                return false;
            }
            tree = find(st -> st.getBlock() instanceof CatTreeBlock
                    && st.getValue(CatTreeBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER, 10);
            if (tree == null || !free(tree.above()) || !level().getBlockState(tree.above(2)).getCollisionShape(level(), tree.above(2)).isEmpty()) {
                return false;
            }
            spot = spotBy(tree, false);
            return spot != null;
        }

        @Override
        public void start() {
            sat = 0;
            length = 600 + random.nextInt(1000);
            getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return sat < length && !isOrderedToSit() && ownerNear(18) && level().getBlockState(tree).getBlock() instanceof CatTreeBlock;
        }

        @Override
        public void tick() {
            if (mood() != PERCHED) {
                if (!near(centre(spot), 0.7)) {
                    walkTo(centre(spot), 1.0, 20);
                    return;
                }
                hop(CatTreeBlock.perch(tree));
                setMood(PERCHED);
            }
            getNavigation().stop();
            sat++;
            if (getOwner() instanceof LivingEntity owner) {
                getLookControl().setLookAt(owner, 20.0F, 20.0F);
            }
        }

        @Override
        public void stop() {
            if (mood() == PERCHED) {
                hopOff(tree);
                if (sat > 200) {
                    cheer(4);
                }
            }
            setMood(AWAKE);
            perchCooldown = 2400 + random.nextInt(3600);
        }
    }

    /** A yarn ball nearby: after it, and batting it about. */
    final class YarnGoal extends Goal {
        private YarnBallEntity ball;
        private int played, length, batWait;

        YarnGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (isOrderedToSit() || tickCount < spinUntil || !ownerNear(14)) {
                return false;
            }
            var balls = level().getEntitiesOfClass(YarnBallEntity.class, getBoundingBox().inflate(12, 4, 12));
            ball = null;
            for (YarnBallEntity b : balls) {
                if (ball == null || b.distanceToSqr(OiiaCatEntity.this) < ball.distanceToSqr(OiiaCatEntity.this)) {
                    ball = b;
                }
            }
            // (one just thrown: straight after it; one lying about: now and then)
            return ball != null && (!ball.still() || yarnCooldown == 0 && random.nextInt(80) == 0);
        }

        @Override
        public void start() {
            played = 0;
            length = 240 + random.nextInt(240);
        }

        @Override
        public boolean canContinueToUse() {
            return played < length && ball.isAlive() && !isOrderedToSit() && ownerNear(16) && ball.distanceToSqr(OiiaCatEntity.this) < 20 * 20;
        }

        @Override
        public void tick() {
            played++;
            setMood(PLAYING);
            getLookControl().setLookAt(ball, 30.0F, 30.0F);
            if (batWait > 0) batWait--;
            if (distanceToSqr(ball) > 0.9 * 0.9) {
                walkTo(ball.position(), 1.3, 5);
                return;
            }
            getNavigation().stop();
            if (batWait == 0 && onGround()) {
                // (a swipe: away from it, a bit to one side)
                Vec3 away = ball.position().subtract(position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5) : away.normalize();
                double side = (random.nextDouble() - 0.5) * 0.8, power = 0.22 + random.nextDouble() * 0.2;
                ball.bat((away.x - away.z * side) * power, 0.08 + random.nextDouble() * 0.1, (away.z + away.x * side) * power);
                batWait = 12 + random.nextInt(20);
                if (random.nextInt(3) == 0) {
                    getJumpControl().jump();
                }
            }
        }

        @Override
        public void stop() {
            setMood(AWAKE);
            yarnCooldown = 400 + random.nextInt(800);
            if (played > 100) {
                cheer(5);
            }
        }
    }

    /** Its owner's holding a feather wand: it stalks the feather, and pounces when it's flicked. */
    final class WandGoal extends Goal {
        private Player owner;
        private int played;
        private long lastFlick;

        WandGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (isOrderedToSit() || !(getOwner() instanceof Player p) || !CatToyItems.holdingWand(p) || p.distanceToSqr(OiiaCatEntity.this) > 12 * 12) {
                return false;
            }
            owner = p;
            return true;
        }

        @Override
        public void start() {
            played = 0;
            lastFlick = CatToyItems.FLICKS.getOrDefault(owner.getUUID(), 0L);
        }

        @Override
        public boolean canContinueToUse() {
            return !isOrderedToSit() && owner.isAlive() && CatToyItems.holdingWand(owner) && owner.distanceToSqr(OiiaCatEntity.this) < 16 * 16;
        }

        @Override
        public void tick() {
            played++;
            setMood(PLAYING);
            Vec3 feather = CatToyItems.feather(owner, 1.0F);
            getLookControl().setLookAt(feather.x, feather.y + 0.1, feather.z);
            long flick = CatToyItems.FLICKS.getOrDefault(owner.getUUID(), 0L);
            if (flick != lastFlick) {
                lastFlick = flick;
                if (onGround() && distanceToSqr(feather) < 4.0 * 4.0) {
                    Vec3 to = feather.subtract(position());
                    setDeltaMovement(to.x * 0.2, 0.42, to.z * 0.2); // (pounce!)
                    getNavigation().stop();
                    return;
                }
            }
            Vec3 me = position().subtract(feather).multiply(1, 0, 1);
            Vec3 crouch = feather.add(me.lengthSqr() < 1.0E-4 ? Vec3.ZERO : me.normalize().scale(1.0));
            if (distanceToSqr(crouch) > 0.5) {
                walkTo(crouch, 0.75, 10); // (a slow stalk)
            } else {
                getNavigation().stop();
            }
            if (played % 200 == 0 && played <= 1000) {
                cheer(1);
            }
        }

        @Override
        public void stop() {
            setMood(AWAKE);
        }
    }

    /** A laser dot about: after it, as fast as it can go, pouncing when it's close. */
    final class LaserGoal extends Goal {
        private Player pointer;
        private Vec3 dot, going;
        private int played, lost;

        LaserGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        private boolean findDot() {
            for (Player p : level().players()) {
                Vec3 d = CatToyItems.dotOf(p);
                if (d != null && d.distanceToSqr(position()) < 16 * 16) {
                    pointer = p;
                    dot = d;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canUse() {
            return !isOrderedToSit() && findDot();
        }

        @Override
        public void start() {
            played = 0;
            lost = 0;
            going = null;
        }

        @Override
        public boolean canContinueToUse() {
            return !isOrderedToSit() && lost < 40;
        }

        @Override
        public void tick() {
            Vec3 d = CatToyItems.dotOf(pointer);
            if (d == null && !findDot()) {
                lost++;
                return;
            }
            if (d != null) {
                dot = d;
            }
            lost = 0;
            played++;
            setMood(PLAYING);
            getLookControl().setLookAt(dot.x, dot.y, dot.z);
            if (going == null || going.distanceToSqr(dot) > 0.6 || getNavigation().isDone()) {
                going = dot;
                getNavigation().moveTo(dot.x, dot.y, dot.z, 1.45);
            }
            if (onGround() && near(dot, 1.4) && random.nextInt(6) == 0) {
                Vec3 to = dot.subtract(position());
                setDeltaMovement(to.x * 0.25, 0.36, to.z * 0.25);
            }
            if (played % 100 == 0 && played <= 600) {
                cheer(1);
            }
        }

        @Override
        public void stop() {
            setMood(AWAKE);
        }
    }
}
