package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * An orrery or planetarium: how fast its planets go (paused, slow, fast, or real time - a year a Minecraft day), real
 * sizes or sizes to see them by, and how far they've gone (kept as "so many years at that tick", so changing speed
 * carries on from where they are). Stopped by a redstone signal; a comparator reads where the Earth is in its year.
 */
public class SolarSystemBlockEntity extends BlockEntity {
    public static final int PAUSED = 0, SLOW = 1, FAST = 2, REAL = 3, SPEEDS = 4;
    private int speed = SLOW;
    private boolean realSizes;
    private int size; // (the planetarium: 0 normal, 1 bigger, 2 a lot bigger)
    private boolean dome = true; // (the planetarium's glass dome, with its dark starry space inside)
    private boolean powered;
    private double baseYears = 26.3, baseSpin;
    private long since;
    private int lastSignal = -1;

    public SolarSystemBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_SYSTEM.get(), pos, state);
    }

    /** Years a tick at each speed: slow a year in 2 minutes, fast in 20 seconds, real a year a Minecraft day. */
    private double rate() {
        if (powered) return 0.0;
        return switch (speed) {
            case SLOW -> 1.0 / 2400.0;
            case FAST -> 1.0 / 400.0;
            case REAL -> 1.0 / 24000.0;
            default -> 0.0;
        };
    }

    public double years(long gameTime, float partialTick) {
        return baseYears + (gameTime + partialTick - since) * rate();
    }

    /** Seconds of the planets turning on their axes (stopped with them). */
    public double spin(long gameTime, float partialTick) {
        return baseSpin + (rate() > 0.0 ? (gameTime + partialTick - since) / 20.0 : 0.0);
    }

    public int speed() {
        return speed;
    }

    /** The planetarium's size: 1, 2 or 4 times. */
    public float scale() {
        return size == 2 ? 4.0F : size == 1 ? 2.0F : 1.0F;
    }

    public int size() {
        return size;
    }

    public void cycleSize() {
        size = (size + 1) % 3;
        changed();
    }

    public boolean dome() {
        return dome;
    }

    public void toggleDome() {
        dome = !dome;
        changed();
    }

    public boolean realSizes() {
        return realSizes;
    }

    private void rebase() {
        long now = level == null ? since : level.getGameTime();
        baseYears = years(now, 0.0F);
        baseSpin = spin(now, 0.0F) % 36000.0;
        since = now;
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void cycleSpeed() {
        rebase();
        speed = (speed + 1) % SPEEDS;
        changed();
    }

    public void toggleSizes() {
        realSizes = !realSizes;
        changed();
    }

    public void setPowered(boolean on) {
        if (on != powered) {
            rebase();
            powered = on;
            changed();
        }
    }

    /** Where the Earth is in its year, 0..15. */
    public int signal() {
        if (level == null) return 0;
        double y = years(level.getGameTime(), 0.0F) + 0.3; // (the Earth's place in its orbit: as the shader's)
        return (int) Math.floor((y - Math.floor(y)) * 15.999);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolarSystemBlockEntity be) {
        if (level.getGameTime() % 10L == 0L) {
            int s = be.signal();
            if (s != be.lastSignal) {
                be.lastSignal = s;
                level.updateNeighbourForOutputSignal(pos, state.getBlock());
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("speed", speed);
        tag.putBoolean("realSizes", realSizes);
        tag.putBoolean("dome", dome);
        tag.putInt("size", size);
        tag.putBoolean("powered", powered);
        tag.putDouble("years", baseYears);
        tag.putDouble("spin", baseSpin);
        tag.putLong("since", since);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("speed")) {
            int was = speed;
            speed = Math.floorMod(tag.getInt("speed"), SPEEDS);
            if (was != speed && level != null && level.isClientSide) { // (test build: told in the log)
                SkyBlocksMod.LOGGER.info("Shadered+ orrery check: the {} at {} (#{}) got speed {} from the server", getBlockState().getBlock() instanceof SolarSystemBlock b && b.hologram() ? "planetarium" : "orrery",
                        worldPosition.toShortString(), Integer.toHexString(System.identityHashCode(this)), speed);
            }
        }
        realSizes = tag.getBoolean("realSizes");
        dome = !tag.contains("dome") || tag.getBoolean("dome");
        size = Math.floorMod(tag.getInt("size"), 3);
        powered = tag.getBoolean("powered");
        if (tag.contains("years")) baseYears = tag.getDouble("years");
        baseSpin = tag.getDouble("spin");
        since = tag.getLong("since");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** What its renderer draws (1.20.1: the block entity says, not the renderer). */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        if (!(getBlockState().getBlock() instanceof SolarSystemBlock b && b.hologram())) {
            return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(0.35, 0.15, 0.35);
        }
        double s = scale();
        return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(1.55 * s - 0.4, 0.0, 1.55 * s - 0.4).expandTowards(0.0, 2.65 * s - 0.7, 0.0);
    }
}
