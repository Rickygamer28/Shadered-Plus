package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What a television (the bottom left one of its wall) plays, and where it is in it - kept on the server as the game
 * time it started at (so everyone watching sees the same moment, and joining later starts where it is), or where it
 * was paused. The clients play it themselves (client/tv/VideoPlayer).
 */
public class TelevisionBlockEntity extends BlockEntity {
    public static final int MAX_SOURCE = 8192;
    /**
     * A YouTube link (a video or a playlist), a link to a video, "file:" and a path on the computer of whoever chose it,
     * or "files:" and several paths (one a line: played in turn).
     */
    private String source = "";
    /** Who chose it (for a file: only their computer has it). */
    private String chosenBy = "";
    /** The game time (ticks) it was at 0; while paused, where it is (ms). */
    private long startTick;
    private boolean paused;
    private long pausedAt;
    private int volume = 70;
    /** Which of a playlist's videos it's at (counted on from where it started; the clients wrap it round). */
    private int item;
    private boolean loop = true;
    /** Bumped on every change: clients seeing it change resync. */
    private int revision;

    public TelevisionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELEVISION.get(), pos, state);
    }

    public String source() {
        return source;
    }

    public String chosenBy() {
        return chosenBy;
    }

    public boolean paused() {
        return paused;
    }

    public int volume() {
        return volume;
    }

    public int item() {
        return item;
    }

    /** On to another of its playlist's videos (only if it's still at {@code from}: everyone watching asks at the end). */
    public void goTo(int from, int to) {
        if (from != item || source.isEmpty()) {
            return;
        }
        item = to;
        paused = false;
        pausedAt = 0;
        startTick = level == null ? 0 : level.getGameTime();
        changed();
    }

    public boolean loop() {
        return loop;
    }

    public int revision() {
        return revision;
    }

    /** Where it should be now (ms; may be past its end: the player wraps it, or stops at its end). */
    public long position(float partialTick) {
        if (paused || level == null) {
            return pausedAt;
        }
        return Math.max(0L, (long) ((level.getGameTime() - startTick + partialTick) * 50.0));
    }

    public void choose(String src, String by) {
        source = src.length() > MAX_SOURCE ? src.substring(0, MAX_SOURCE) : src;
        chosenBy = by;
        item = 0;
        paused = false;
        pausedAt = 0;
        startTick = level == null ? 0 : level.getGameTime();
        changed();
    }

    public void play(boolean play) {
        if (play == !paused) {
            return;
        }
        if (play) {
            startTick = (level == null ? 0 : level.getGameTime()) - pausedAt / 50;
        } else {
            pausedAt = position(0.0F);
        }
        paused = !play;
        changed();
    }

    public void stop() {
        source = "";
        paused = false;
        pausedAt = 0;
        changed();
    }

    public void seek(long ms) {
        ms = Math.max(0, ms);
        if (paused) {
            pausedAt = ms;
        } else {
            startTick = (level == null ? 0 : level.getGameTime()) - ms / 50;
        }
        changed();
    }

    public void setVolume(int v) {
        volume = Math.max(0, Math.min(100, v));
        changed();
    }

    public void setLoop(boolean l) {
        loop = l;
        changed();
    }

    void copyFrom(TelevisionBlockEntity o) {
        source = o.source;
        chosenBy = o.chosenBy;
        startTick = o.startTick;
        paused = o.paused;
        pausedAt = o.pausedAt;
        volume = o.volume;
        loop = o.loop;
        item = o.item;
        changed();
    }

    private void changed() {
        revision++;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("source", source);
        tag.putString("by", chosenBy);
        tag.putLong("start", startTick);
        tag.putBoolean("paused", paused);
        tag.putLong("at", pausedAt);
        tag.putInt("volume", volume);
        tag.putBoolean("loop", loop);
        tag.putInt("rev", revision);
        tag.putInt("item", item);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        source = tag.getString("source");
        chosenBy = tag.getString("by");
        startTick = tag.getLong("start");
        paused = tag.getBoolean("paused");
        pausedAt = tag.getLong("at");
        volume = tag.contains("volume") ? Math.max(0, Math.min(100, tag.getInt("volume"))) : 70;
        loop = !tag.contains("loop") || tag.getBoolean("loop");
        revision = tag.getInt("rev");
        item = tag.getInt("item");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
