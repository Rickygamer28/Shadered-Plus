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
public class TelevisionBlockEntity extends BlockEntity implements TvState {
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
    /** Its playlist in a random order (the same for everyone: from this seed), starting at the video it was at. */
    private boolean shuffle;
    private long shuffleSeed;
    private int shuffleFirst;
    private boolean loop = true;
    /** Its saved channels (links; "" empty), and which it's on (-1 none). */
    public static final int CHANNELS = 9, MAX_QUEUE = 32;
    private final String[] channels = new String[CHANNELS];
    private int channel = -1;
    private int playId;
    /** What plays after this, in turn: a link and who added it. */
    public record Queued(String source, String by) {}
    private final java.util.List<Queued> queue = new java.util.ArrayList<>();

    {
        java.util.Arrays.fill(channels, "");
    }

    /** Bumped on every change: clients seeing it change resync. */
    private int revision;

    public TelevisionBlockEntity(BlockPos pos, BlockState state) {
        super(state.getBlock() instanceof ProjectorBlock ? ModBlockEntities.PROJECTOR.get() : ModBlockEntities.TELEVISION.get(), pos, state);
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

    public String channel(int i) {
        return i >= 0 && i < CHANNELS ? channels[i] : "";
    }

    public int channel() {
        return channel;
    }

    public java.util.List<Queued> queue() {
        return java.util.Collections.unmodifiableList(queue);
    }

    @Override
    public int playId() {
        return playId;
    }

    @Override
    public int queueSize() {
        return queue.size();
    }

    /** Saves a link (or, given none, what's on) to a channel. */
    public void saveChannel(int i, String link) {
        if (i < 0 || i >= CHANNELS) return;
        String l = link.isEmpty() ? source : link;
        channels[i] = l.length() > MAX_SOURCE ? l.substring(0, MAX_SOURCE) : l;
        if (!l.isEmpty() && l.equals(source)) channel = i;
        changed();
    }

    public void playChannel(int i, String by) {
        if (i < 0 || i >= CHANNELS || channels[i].isEmpty()) return;
        choose(channels[i], by);
        channel = i;
        changed();
    }

    /** On to the next saved channel up or down from the one it's on (round). */
    public void stepChannel(int dir, String by) {
        for (int k = 1; k <= CHANNELS; k++) {
            int i = Math.floorMod((channel < 0 ? (dir > 0 ? -1 : 0) : channel) + dir * k, CHANNELS);
            if (!channels[i].isEmpty()) {
                playChannel(i, by);
                return;
            }
        }
    }

    /** What a tablet plays, from where it is in it, onto this (cast). */
    public void castFrom(TabletState s, String by) {
        source = s.source();
        chosenBy = by;
        playId++;
        channel = -1;
        item = s.item();
        shuffle = s.shuffle();
        shuffleSeed = s.shuffleSeed();
        shuffleFirst = s.shuffleFirst();
        paused = s.paused();
        pausedAt = s.at();
        startTick = s.start();
        loop = s.loop();
        changed();
    }

    public void enqueue(String link, String by) {
        if (link.isEmpty()) return;
        if (source.isEmpty()) {
            choose(link, by); // (nothing on: it plays now)
            return;
        }
        if (queue.size() < MAX_QUEUE) {
            queue.add(new Queued(link.length() > MAX_SOURCE ? link.substring(0, MAX_SOURCE) : link, by));
            changed();
        }
    }

    /** The next in its queue (only if it's still on {@code current}: everyone watching asks at its end). */
    public void nextInQueue(String current) {
        if (queue.isEmpty() || !current.equals(source)) return;
        Queued q = queue.remove(0);
        choose(q.source(), q.by());
    }

    public void clearQueue() {
        queue.clear();
        changed();
    }

    public void removeFromQueue(int i) {
        if (i >= 0 && i < queue.size()) {
            queue.remove(i);
            changed();
        }
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

    public boolean shuffle() {
        return shuffle;
    }

    public long shuffleSeed() {
        return shuffleSeed;
    }

    public int shuffleFirst() {
        return shuffleFirst;
    }

    /**
     * Random order on (a new one, starting at {@code at} - the video playing) or off; {@code newItem}: the count that
     * keeps that video playing in the new order (the time goes on).
     */
    public void setShuffle(boolean on, int at, int newItem) {
        shuffle = on;
        if (on) {
            shuffleSeed = level == null ? 0 : level.getRandom().nextLong();
            shuffleFirst = at;
        }
        item = newItem;
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
        playId++;
        channel = -1;
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
        System.arraycopy(o.channels, 0, channels, 0, CHANNELS);
        channel = o.channel;
        queue.clear();
        queue.addAll(o.queue);
        shuffle = o.shuffle;
        shuffleSeed = o.shuffleSeed;
        shuffleFirst = o.shuffleFirst;
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
        net.minecraft.nbt.ListTag ch = new net.minecraft.nbt.ListTag();
        for (String c : channels) ch.add(net.minecraft.nbt.StringTag.valueOf(c));
        tag.put("channels", ch);
        tag.putInt("channel", channel);
        tag.putInt("play", playId);
        net.minecraft.nbt.ListTag qu = new net.minecraft.nbt.ListTag();
        for (Queued q : queue) {
            CompoundTag e = new CompoundTag();
            e.putString("s", q.source());
            e.putString("by", q.by());
            qu.add(e);
        }
        tag.put("queue", qu);
        tag.putBoolean("shuffle", shuffle);
        tag.putLong("seed", shuffleSeed);
        tag.putInt("first", shuffleFirst);
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
        java.util.Arrays.fill(channels, "");
        net.minecraft.nbt.ListTag ch = tag.getList("channels", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < Math.min(CHANNELS, ch.size()); i++) channels[i] = ch.getString(i);
        channel = tag.contains("channel") ? tag.getInt("channel") : -1;
        playId = tag.getInt("play");
        queue.clear();
        net.minecraft.nbt.ListTag qu = tag.getList("queue", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < qu.size(); i++) queue.add(new Queued(qu.getCompound(i).getString("s"), qu.getCompound(i).getString("by")));
        shuffle = tag.getBoolean("shuffle");
        shuffleSeed = tag.getLong("seed");
        shuffleFirst = tag.getInt("first");
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
