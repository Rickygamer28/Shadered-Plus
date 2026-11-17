package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * An hourglass timer (HourglassBlock): turned over (from its screen, or by a redstone signal), its sand runs for as
 * long as it's set (1 second .. 1 hour); run out, a redstone pulse - and, looping, it turns itself over and runs again.
 * A comparator reads how much sand has fallen. Its run is the time it was turned over and how long it lasts, known to
 * the client alike (client/HourglassRenderer draws the sand from that).
 */
public class HourglassBlockEntity extends BlockEntity {
    /** How long it runs (ticks), as it can be set. */
    public static final int[] DURATIONS = {20, 40, 100, 200, 600, 1200, 2400, 6000, 12000, 36000, 72000};
    /** How long its pulse lasts, and its turning over (ticks). */
    public static final int PULSE = 4, FLIP = 10;

    private int duration = 3;
    private boolean loop;
    /** When its sand started running (game time), or -1: not running (its sand all fallen, or never turned). */
    private long start = -1;
    /** When it was last turned over (game time; for the turning, drawn). */
    private long flipped = Long.MIN_VALUE / 2;
    private long pulseUntil = -1;

    public HourglassBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOURGLASS.get(), pos, state);
    }

    public int durationIndex() {
        return duration;
    }

    public int ticks() {
        return DURATIONS[duration];
    }

    public boolean loop() {
        return loop;
    }

    public boolean running() {
        return start >= 0;
    }

    public long flipped() {
        return flipped;
    }

    /** How much of its sand has fallen (0 .. 1), now; all of it if not running (until it's turned over again). */
    public float fallen(float partialTick) {
        if (level == null) {
            return 1.0F;
        }
        if (start < 0) {
            return 1.0F;
        }
        return Mth.clamp((level.getGameTime() - start + partialTick) / ticks(), 0.0F, 1.0F);
    }

    public void setDuration(int i) {
        duration = Mth.clamp(i, 0, DURATIONS.length - 1);
        changed();
    }

    public void toggleLoop() {
        loop = !loop;
        changed();
    }

    /** Turned over: its sand runs from the start. */
    public void turn() {
        if (level == null) {
            return;
        }
        start = level.getGameTime() + FLIP; // (it runs once it's turned)
        flipped = level.getGameTime();
        level.playSound(null, worldPosition, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.5F, 1.6F);
        changed();
    }

    public boolean powered() {
        return level != null && level.getGameTime() < pulseUntil;
    }

    public int comparator() {
        return Math.round(fallen(0.0F) * 15.0F);
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HourglassBlockEntity be) {
        long now = level.getGameTime();
        if (be.start >= 0 && now - be.start >= be.ticks()) {
            // run out: a pulse (and, looping, turned over again)
            be.start = -1;
            be.pulseUntil = now + PULSE;
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6F, 1.8F);
            level.updateNeighborsAt(pos, state.getBlock());
            if (be.loop) {
                be.turn();
            } else {
                be.changed();
            }
        }
        if (be.pulseUntil >= 0 && now == be.pulseUntil) {
            level.updateNeighborsAt(pos, state.getBlock());
        }
        if (be.start >= 0 && now % 10 == 0) {
            level.updateNeighbourForOutputSignal(pos, state.getBlock()); // (comparators: how much has fallen)
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        save(tag);
    }

    private void save(CompoundTag tag) {
        tag.putInt("duration", duration);
        tag.putBoolean("loop", loop);
        tag.putLong("start", start);
        tag.putLong("flipped", flipped);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        duration = tag.contains("duration") ? Mth.clamp(tag.getInt("duration"), 0, DURATIONS.length - 1) : 3;
        loop = tag.getBoolean("loop");
        start = tag.contains("start") ? tag.getLong("start") : -1;
        flipped = tag.contains("flipped") ? tag.getLong("flipped") : Long.MIN_VALUE / 2;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        save(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** A duration, for showing: "1s", "30s", "5m", "1h". */
    public static String label(int ticks) {
        int s = ticks / 20;
        return s < 60 ? s + "s" : s < 3600 ? s / 60 + "m" : s / 3600 + "h";
    }
}
