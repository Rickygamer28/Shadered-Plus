package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * The time crystal: switched on, it hurries time on round it - in a circle (its radius: 4, 8 or 12 blocks), from two
 * below it to six above: crops, saplings and everything else that grows (more random ticks), furnaces, brewing stands
 * and other machines (their ticks run again), all x4, x16, x64 or x256. It needs nothing to run, and it's light enough
 * for what grows: crops and saplings grow round it in the dark too.
 * Not sped up: other time crystals, spin pads and power cores, anything giving energy out (no free energy), and the
 * blocks tagged skyblocks:time_crystal_immune.
 * Its looks (client/TimeCrystalRenderer): rune rings spinning round the crystal, a clock face turning on the ground, and
 * a dome over what it hurries, its sky running through day and night as fast as it's set.
 */
public class TimeCrystalBlockEntity extends BlockEntity {
    public static final int[] SPEEDS = {4, 16, 64, 256};
    public static final int[] RADII = {4, 8, 12};
    /** Below and above it, how far it reaches. */
    public static final int DOWN = 2, UP = 6;
    /** Most extra block-entity ticks in one tick (a busy base: the server keeps up). */
    private static final int MOST_TICKS = 4096;
    public static final TagKey<Block> IMMUNE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "time_crystal_immune"));

    private int speed = 1, radius = 1;
    private boolean on = true, dome = true;
    /** Whether it's hurrying time right now (as the client sees it: switched on). */
    private boolean running;
    private float randomOwed;
    private final List<BlockPos> machines = new ArrayList<>();

    /** (client) Its rings' turn, eased up to speed and down when it stops. */
    public float spin, spinSpeed, shownOn;

    public TimeCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIME_CRYSTAL.get(), pos, state);
    }

    public int speed() {
        return SPEEDS[speed];
    }

    public int speedIndex() {
        return speed;
    }

    public int radius() {
        return RADII[radius];
    }

    public int radiusIndex() {
        return radius;
    }

    public boolean on() {
        return on;
    }

    public boolean dome() {
        return dome;
    }

    public boolean running() {
        return running;
    }

    public void setSpeed(int i) {
        speed = Math.floorMod(i, SPEEDS.length);
        changed();
    }

    public void setRadius(int i) {
        radius = Math.floorMod(i, RADII.length);
        machines.clear();
        changed();
    }

    public void toggleOn() {
        on = !on;
        changed();
    }

    public void toggleDome() {
        dome = !dome;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TimeCrystalBlockEntity be) {
        if (be.on != be.running) {
            be.running = be.on;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        if (be.on) {
            be.hurry((ServerLevel) level);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TimeCrystalBlockEntity be) {
        float want = be.running ? 0.06F + 0.05F * be.speed : 0.0F;
        be.spinSpeed += (want - be.spinSpeed) * 0.05F;
        be.spin += be.spinSpeed;
        be.shownOn += ((be.running ? 1.0F : 0.0F) - be.shownOn) * 0.06F;
    }

    /** Time hurried on round it, for one tick. */
    private void hurry(ServerLevel level) {
        int r = radius(), extra = speed() - 1;
        BlockPos c = worldPosition;
        // what grows: as many more random ticks as the game gives this much space each tick, times the extra speed
        long blocks = (long) (2 * r + 1) * (2 * r + 1) * (DOWN + UP + 1);
        randomOwed += extra * blocks * level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING) / 4096.0F;
        int n = Math.min((int) randomOwed, 4096);
        randomOwed -= (int) randomOwed;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            int dx = level.random.nextInt(2 * r + 1) - r, dz = level.random.nextInt(2 * r + 1) - r;
            if (dx * dx + dz * dz > r * r + r) {
                continue; // (a circle)
            }
            p.set(c.getX() + dx, c.getY() - DOWN + level.random.nextInt(DOWN + UP + 1), c.getZ() + dz);
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (s.is(IMMUNE)) {
                continue;
            }
            // (crops and saplings: grown in the dark too - the crystal's light is enough; in the light, as they would)
            if (s.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && level.getRawBrightness(p, 0) < 9) {
                if (!crop.isMaxAge(s) && level.random.nextInt(8) == 0) {
                    level.setBlock(p, crop.getStateForAge(crop.getAge(s) + 1), Block.UPDATE_CLIENTS);
                }
            } else if (s.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock sapling && level.getMaxLocalRawBrightness(p.above()) < 9) {
                if (level.random.nextInt(7) == 0) {
                    sapling.advanceTree(level, p.immutable(), s, level.random);
                }
            } else if (s.isRandomlyTicking()) {
                s.randomTick(level, p.immutable(), level.random);
            }
            net.minecraft.world.level.material.FluidState f = s.getFluidState();
            if (f.isRandomlyTicking()) {
                f.randomTick(level, p.immutable(), level.random);
            }
        }
        // machines: their ticks run again (the list found again every second)
        if (machines.isEmpty() || level.getGameTime() % 20 == 0) {
            findMachines(level);
        }
        int budget = MOST_TICKS;
        for (BlockPos m : machines) {
            BlockEntity be = level.getBlockEntity(m);
            if (be == null || be.isRemoved()) {
                continue;
            }
            budget -= tick(level, be, Math.min(extra, budget));
            if (budget <= 0) {
                break;
            }
        }
    }

    private void findMachines(ServerLevel level) {
        machines.clear();
        int r = radius();
        BlockPos c = worldPosition;
        for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++) {
            for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos b = be.getBlockPos();
                    int dx = b.getX() - c.getX(), dy = b.getY() - c.getY(), dz = b.getZ() - c.getZ();
                    if (dx * dx + dz * dz > r * r + r || dy < -DOWN || dy > UP || !hurries(level, be)) {
                        continue;
                    }
                    machines.add(b.immutable());
                }
            }
        }
    }

    /** Whether it hurries this one. */
    private static boolean hurries(Level level, BlockEntity be) {
        if (be instanceof TimeCrystalBlockEntity || be instanceof SpinPadBlockEntity || be instanceof PowerCoreBlockEntity
                || be instanceof CoreFrameBlockEntity || be.getBlockState().is(IMMUNE)) {
            return false;
        }
        // (anything that gives energy out - a generator: sped up, it'd make energy out of nothing)
        IEnergyStorage e = level.getCapability(Capabilities.EnergyStorage.BLOCK, be.getBlockPos(), null);
        if (e != null && e.canExtract()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        BlockEntityTicker<BlockEntity> ticker = be.getBlockState().getTicker(level, (BlockEntityType<BlockEntity>) be.getType());
        return ticker != null;
    }

    /** A block entity's tick run again (up to times); how many ran. */
    @SuppressWarnings("unchecked")
    private static int tick(Level level, BlockEntity be, int times) {
        int ran = 0;
        for (int i = 0; i < times && !be.isRemoved(); i++) {
            BlockState s = be.getBlockState();
            BlockEntityTicker<BlockEntity> ticker = s.getTicker(level, (BlockEntityType<BlockEntity>) be.getType());
            if (ticker == null) {
                break;
            }
            try {
                ticker.tick(level, be.getBlockPos(), s, be);
            } catch (RuntimeException e) {
                SkyBlocksMod.LOGGER.warn("Shadered+: a time crystal couldn't hurry {} at {}", be.getType(), be.getBlockPos(), e);
                return times;
            }
            ran++;
        }
        return ran;
    }

    // ---- saving, and what the client sees ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        save(tag);
    }

    private void save(CompoundTag tag) {
        tag.putInt("speed", speed);
        tag.putInt("radius", radius);
        tag.putBoolean("off", !on);
        tag.putBoolean("no_dome", !dome);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        speed = tag.contains("speed") ? Math.floorMod(tag.getInt("speed"), SPEEDS.length) : 1;
        radius = tag.contains("radius") ? Math.floorMod(tag.getInt("radius"), RADII.length) : 1;
        on = !tag.getBoolean("off");
        dome = !tag.getBoolean("no_dome");
        running = tag.getBoolean("running");
        machines.clear();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        save(tag);
        tag.putBoolean("running", running);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
