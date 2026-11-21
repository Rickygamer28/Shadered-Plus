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
 * An elevator's cabin (ElevatorBaseBlockEntity): a floor as wide as its shaft (1 .. 7 across) you can stand on, that rides
 * up and down it.
 * Players on it as it moves stand on it and walk about on it (held on its floor by their own client:
 * client/ElevatorButtons.keepOn; the server not taking them for flying); mobs ride it - held where they stood on it,
 * standing - and are set down on the floor it stops at. Its trip (from, to, when it set off, how long it takes) is known to the server and
 * to every client alike, each working out where it is from the time - so it moves smoothly, with what it carries.
 * Its floor buttons are inside it (client/ElevatorButtons); sneak + right-click it: its look and speed
 * (client/ElevatorScreen). It draws nothing of its own: its base draws it.
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
    /** How wide it is, how tall (blocks), and its middle from its base's middle (x, z). */
    private static final EntityDataAccessor<Integer> WIDTH = SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEIGHT = SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> OFF_X = SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFF_Z = SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.FLOAT);

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
        if (level.getBlockEntity(base) instanceof ElevatorBaseBlockEntity be) {
            c.shape(be.width(), (float) (be.centreX() - base.getX() - 0.5), (float) (be.centreZ() - base.getZ() - 0.5), be.cabinHeight());
        }
        c.setPos(c.middleX(), landing - PLATE, c.middleZ());
        return c;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BASE, BlockPos.ZERO);
        builder.define(FROM, 0.0F);
        builder.define(TO, 0.0F);
        builder.define(START, 0L);
        builder.define(TIME, 1);
        builder.define(WIDTH, 3);
        builder.define(HEIGHT, 3);
        builder.define(OFF_X, 0.0F);
        builder.define(OFF_Z, 0.0F);
    }

    public BlockPos base() {
        return entityData.get(BASE);
    }

    /** Its size and place (its base's: as its shaft is now). */
    public void shape(int width, float offX, float offZ, int height) {
        if (entityData.get(WIDTH) != width) {
            entityData.set(WIDTH, width);
        }
        if (entityData.get(HEIGHT) != height) {
            entityData.set(HEIGHT, height);
        }
        if (entityData.get(OFF_X) != offX) {
            entityData.set(OFF_X, offX);
        }
        if (entityData.get(OFF_Z) != offZ) {
            entityData.set(OFF_Z, offZ);
        }
    }

    /** Half its width (blocks). */
    public float half() {
        return entityData.get(WIDTH) / 2.0F;
    }

    public int cabinHeight() {
        return entityData.get(HEIGHT);
    }

    private double middleX() {
        return base().getX() + 0.5 + entityData.get(OFF_X);
    }

    private double middleZ() {
        return base().getZ() + 0.5 + entityData.get(OFF_Z);
    }

    /** Its floor: a slab as wide as it, its top where its riders stand. */
    @Override
    protected net.minecraft.world.phys.AABB makeBoundingBox() {
        float h = half() - 0.03F;
        return new net.minecraft.world.phys.AABB(getX() - h, getY(), getZ() - h, getX() + h, getY() + PLATE, getZ() + h);
    }

    /** Where it'll stop (y of its floor's top). */
    public float target() {
        return entityData.get(TO);
    }

    /** (client) The trip it last saw start, and its own tick count then less how long it had been going. */
    private long seenStart = Long.MIN_VALUE;
    private int tripFrom;

    /**
     * How long it's been on its trip (ticks). On a client, counted by its own ticks from when it saw the trip start: the
     * game's time there is put right by the server now and then - a tick back, sometimes - and the cabin went back with
     * it (a jolt down as it rose, its riders dropping onto it).
     */
    private float elapsed(float partialTick) {
        long start = entityData.get(START);
        if (!level().isClientSide) {
            return level().getGameTime() - start + partialTick;
        }
        if (start != seenStart) {
            seenStart = start;
            tripFrom = tickCount - (int) Mth.clamp(level().getGameTime() - start, 0L, 1_000_000L);
        }
        return tickCount - tripFrom + partialTick;
    }

    /** How far along its trip it is (0 .. 1). */
    private float along(float partialTick) {
        float t = elapsed(partialTick) / Math.max(1, entityData.get(TIME));
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

    /**
     * (client) Its floor's top as it's drawn: between where it was last tick and where it is, as its riders are drawn (so
     * they stand on it - worked out from the time instead, it was a tick ahead of them: sunk into it going up, floating
     * over it going down).
     */
    public float shownLanding(float partialTick) {
        return (float) Mth.lerp(partialTick, yo, getY()) + PLATE;
    }

    /** How fast it's going (blocks a tick, up +), now. */
    public float velocity(float partialTick) {
        return landing(partialTick + 0.5F) - landing(partialTick - 0.5F);
    }

    public boolean moving() {
        return elapsed(0.0F) < entityData.get(TIME);
    }

    /** How long after it stops it's still taken as moving, for what's on it (the server is a little ahead of the players'). */
    private static final int SETTLE = 12;

    /** Whether it has stopped, and a little while since: solid again, its riders let off. */
    public boolean settled() {
        return elapsed(0.0F) >= entityData.get(TIME) + SETTLE;
    }

    /** Off to a floor: everything standing on it rides it. */
    public void go(int to, float speed) {
        float from = landing();
        int time = Math.max(30, Math.round(Math.abs(to - from) / speed * 1.5F)); // (eased: its top speed 1.5x the average)
        letting = false;
        BlockPos b = base();
        List<Entity> on = level().getEntities(this, getBoundingBox().inflate(0.0, 2.0, 0.0),
                e -> e instanceof LivingEntity && !(e instanceof Player) && !e.isPassenger() && !e.isSpectator()
                        && ElevatorBaseBlockEntity.standingIn(e, getX(), getZ(), half(), from));
        for (Entity e : on) {
            e.startRiding(this, true);
        }
        entityData.set(FROM, from);
        entityData.set(TO, (float) to);
        entityData.set(START, level().getGameTime());
        entityData.set(TIME, time);
        level().playSound(null, middleX(), from + 1.0, middleZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6F, 1.7F);
    }

    @Override
    public void tick() {
        super.tick();
        BlockPos b = base();
        setPos(middleX(), landing() - PLATE, middleZ());
        if (level().isClientSide) {
            if (!settled()) {
                com.example.skyblocks.client.ElevatorButtons.keepOn(this);
            }
            return;
        }
        if (!settled()) {
            // (anything that lands on it - or that it comes up under - on the way: it rides too, rather than fall through)
            float landing = landing(), reach = 0.6F + Math.abs(velocity(0.0F)) * 2.0F;
            for (Entity e : level().getEntities(this, getBoundingBox().inflate(0.0, 2.5, 0.0),
                    e -> e instanceof LivingEntity && !(e instanceof Player) && !e.isPassenger() && !e.isSpectator() && Math.abs(e.getX() - getX()) < half() - 0.05
                            && Math.abs(e.getZ() - getZ()) < half() - 0.05 && e.getY() > landing - reach && e.getY() < landing + 0.35)) {
                e.startRiding(this, true);
            }
            // players on it: not falling, not flying (standing on a cabin, not a block, the server would think they were)
            for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.0, 3.0, 0.0),
                    p -> Math.abs(p.getX() - getX()) < half() + 0.1 && Math.abs(p.getZ() - getZ()) < half() + 0.1 && p.getY() > landing - 1.5 && p.getY() < landing + 2.0)) {
                p.fallDistance = 0.0F;
                if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
                    notFlying(sp);
                }
            }
        }
        if (tickCount % 20 == 0 && !(level().getBlockEntity(b) instanceof ElevatorBaseBlockEntity)) {
            release();
            discard();
            return;
        }
        if (settled() && isVehicle()) {
            release();
            level().playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8F, 1.5F);
        }
        if (!isVehicle() && !riders.isEmpty() && settled()) {
            riders.clear();
        }
    }

    private static java.lang.reflect.Field aboveGround;
    private static boolean noField;

    /** A player standing on a moving cabin isn't kicked for flying (their time off the ground counted again from 0). */
    private static void notFlying(net.minecraft.server.level.ServerPlayer sp) {
        if (noField) {
            return;
        }
        try {
            if (aboveGround == null) {
                aboveGround = net.neoforged.fml.util.ObfuscationReflectionHelper.findField(
                        net.minecraft.server.network.ServerGamePacketListenerImpl.class, "aboveGroundTickCount");
            }
            aboveGround.setInt(sp.connection, 0);
        } catch (RuntimeException | IllegalAccessException e) {
            noField = true;
            SkyBlocksMod.LOGGER.warn("Shadered+: couldn't keep elevator riders from counting as flying", e);
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
        double h = Math.max(0.0, half() - 0.3);
        riders.put(passenger.getId(), new Vec3(Mth.clamp(passenger.getX() - getX(), -h, h), 0.0, Mth.clamp(passenger.getZ() - getZ(), -h, h)));
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

    /**
     * Solid to stand on - when it's still. Moving, not: its riders are held on it (players by their own client, mobs
     * riding it), and the server, a little ahead of a player's client, found them in its floor and put them back where
     * they'd come from - a drop as it rose.
     */
    @Override
    public boolean canBeCollidedWith() {
        // (the server: only once it's settled - a player's client, a little behind, still has them riding it up)
        return level().isClientSide ? !moving() : settled();
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
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
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

    /** Not got off on the way by sneaking: only where it stops (teleporting away, or anything else, still takes you off). */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof ElevatorCabinEntity c && !c.letting && c.moving()
                && event.getEntityMounting().isShiftKeyDown() && event.getEntityMounting().isAlive() && !event.getEntityMounting().isRemoved()) {
            event.setCanceled(true);
        }
    }
}
