package com.example.skyblocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/**
 * An elevator's cabin (ElevatorBaseBlockEntity): a floor 3 x 3 you can stand on, that rides up and down its shaft.
 * Sent off, everything standing on it rides it - held where they stood on it, standing, not let off on the way - and
 * set down on the floor it stops at. Its trip (from, to, when it set off, how long it takes) is known to the server and
 * to every client alike, each working out where it is from the time - so it moves smoothly, with what it carries.
 * Right-click its floor: the floors to go to (client/ElevatorScreen). It draws nothing of its own: its base draws it.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ElevatorCabinEntity extends Entity {
    /** Its floor's thickness (the entity: a slab, its top where its riders stand). */
    public static final float PLATE = 0.25F;
    private static final EntityDataAccessor<BlockPos> BASE = SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> FROM = SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TO = SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> TIME = SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.INT);

    /** Where each rider stands on it (x, z from its middle), kept until it's let off. */
    private final Map<Integer, Vec3> riders = new HashMap<>();
    private boolean letting;

    public ElevatorCabinEntity(EntityType<? extends ElevatorCabinEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static ElevatorCabinEntity make(Level level, BlockPos base, int landing) {
        ElevatorCabinEntity c = new ElevatorCabinEntity(ModEntities.ELEVATOR_CABIN.get(), level);
        c.entityData.set(BASE, base);
        c.entityData.set(FROM, (float) landing);
        c.entityData.set(TO, (float) landing);
        c.entityData.set(START, level.getGameTime() - 1000);
        c.entityData.set(TIME, 1);
        c.setPos(base.getX() + 0.5, landing - PLATE, base.getZ() + 0.5);
        return c;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BASE, BlockPos.ZERO);
        builder.define(FROM, 0.0F);
        builder.define(TO, 0.0F);
        builder.define(START, 0L);
        builder.define(TIME, 1);
    }

    public BlockPos base() {
        return entityData.get(BASE);
    }

    /** Where it'll stop (y of its floor's top). */
    public float target() {
        return entityData.get(TO);
    }

    /** How far along its trip it is (0 .. 1). */
    private float along(float partialTick) {
        float t = (level().getGameTime() - entityData.get(START) + partialTick) / Math.max(1, entityData.get(TIME));
        return Mth.clamp(t, 0.0F, 1.0F);
    }

    /** Easing in and out of a trip: speeding up, gliding, slowing to a stop. */
    private static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    /** Its floor's top (y), now. */
    public float landing() {
        return landing(0.0F);
    }

    public float landing(float partialTick) {
        float from = entityData.get(FROM), to = entityData.get(TO);
        return from + (to - from) * ease(along(partialTick));
    }

    /** How fast it's going (blocks a tick, up +), now. */
    public float velocity(float partialTick) {
        return landing(partialTick + 0.5F) - landing(partialTick - 0.5F);
    }

    public boolean moving() {
        return level().getGameTime() - entityData.get(START) < entityData.get(TIME);
    }

    /** Off to a floor: everything standing on it rides it. */
    public void go(int to, float speed) {
        float from = landing();
        int time = Math.max(30, Math.round(Math.abs(to - from) / speed * 1.5F)); // (eased: its top speed 1.5x the average)
        letting = false;
        BlockPos b = base();
        List<Entity> on = level().getEntities(this, getBoundingBox().inflate(0.0, 2.0, 0.0),
                e -> e instanceof LivingEntity && !e.isPassenger() && !e.isSpectator() && ElevatorBaseBlockEntity.standingIn(e, getX(), getZ(), from));
        for (Entity e : on) {
            e.startRiding(this, true);
        }
        entityData.set(FROM, from);
        entityData.set(TO, (float) to);
        entityData.set(START, level().getGameTime());
        entityData.set(TIME, time);
        level().playSound(null, b.getX() + 0.5, from + 1.0, b.getZ() + 0.5, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6F, 1.7F);
    }

    @Override
    public void tick() {
        super.tick();
        BlockPos b = base();
        setPos(b.getX() + 0.5, landing() - PLATE, b.getZ() + 0.5);
        if (level().isClientSide) {
            return;
        }
        if (tickCount % 20 == 0 && !(level().getBlockEntity(b) instanceof ElevatorBaseBlockEntity)) {
            release();
            discard();
            return;
        }
        if (!moving() && isVehicle()) {
            release();
            level().playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8F, 1.5F);
        }
        if (!isVehicle() && !riders.isEmpty() && !moving()) {
            riders.clear();
        }
    }

    /** Its riders let off, each where it stood. */
    public void release() {
        letting = true;
        ejectPassengers();
        letting = false;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        riders.put(passenger.getId(), new Vec3(Mth.clamp(passenger.getX() - getX(), -1.2, 1.2), 0.0, Mth.clamp(passenger.getZ() - getZ(), -1.2, 1.2)));
        super.addPassenger(passenger);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < 24;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        Vec3 o = riders.getOrDefault(passenger.getId(), Vec3.ZERO);
        move.accept(passenger, getX() + o.x, getY() + PLATE + 0.001, getZ() + o.z);
        passenger.fallDistance = 0.0F;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 o = riders.getOrDefault(passenger.getId(), Vec3.ZERO);
        return new Vec3(getX() + o.x, getY() + PLATE + 0.01, getZ() + o.z);
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            com.example.skyblocks.client.ElevatorScreen.open(base());
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // (where it is is worked out here, from its trip: what the server sends is left alone)
    }

    @Override
    public boolean shouldBeSaved() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        BlockPos b = BlockPos.of(tag.getLong("base"));
        entityData.set(BASE, b);
        float at = tag.getFloat("at");
        entityData.set(FROM, at);
        entityData.set(TO, at);
        entityData.set(START, 0L);
        entityData.set(TIME, 1);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("base", base().asLong());
        tag.putFloat("at", Math.round(target())); // (saved mid-trip: back where it was going)
    }

    /** Not let off on the way (sneaking, say): only where it stops. */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof ElevatorCabinEntity c && !c.letting && c.moving()
                && event.getEntityMounting().isAlive() && !event.getEntityMounting().isRemoved()) {
            event.setCanceled(true);
        }
    }
}
