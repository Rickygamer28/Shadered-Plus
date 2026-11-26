package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;

/**
 * A tablet's screen (kept on the item, so everyone near the one holding it knows what it plays): the same as a
 * television's (TelevisionBlockEntity) - what, from which game time, paused where, volume, repeat, playlist - and its
 * own id (its player on each computer).
 */
public record TabletState(UUID id, String source, String chosenBy, long start, boolean paused, long at, int volume, boolean loop, int item,
                          boolean shuffle, long shuffleSeed, int shuffleFirst) {
    public static final TabletState EMPTY = new TabletState(new UUID(0, 0), "", "", 0, false, 0, 70, true, 0, false, 0, 0);

    public static final com.mojang.serialization.Codec<TabletState> CODEC = CompoundTag.CODEC.xmap(TabletState::load, TabletState::save);
    public static final StreamCodec<ByteBuf, TabletState> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(TabletState::load, TabletState::save);

    private CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("source", source);
        t.putString("by", chosenBy);
        t.putLong("start", start);
        t.putBoolean("paused", paused);
        t.putLong("at", at);
        t.putInt("volume", volume);
        t.putBoolean("loop", loop);
        t.putInt("item", item);
        t.putBoolean("shuffle", shuffle);
        t.putLong("seed", shuffleSeed);
        t.putInt("first", shuffleFirst);
        return t;
    }

    private static TabletState load(CompoundTag t) {
        return new TabletState(t.hasUUID("id") ? t.getUUID("id") : new UUID(0, 0), t.getString("source"), t.getString("by"), t.getLong("start"),
                t.getBoolean("paused"), t.getLong("at"), t.contains("volume") ? Math.max(0, Math.min(100, t.getInt("volume"))) : 70,
                !t.contains("loop") || t.getBoolean("loop"), t.getInt("item"), t.getBoolean("shuffle"), t.getLong("seed"), t.getInt("first"));
    }

    /** Where it should be (ms) at this game time. */
    public long position(long gameTime, float partialTick) {
        return paused ? at : Math.max(0L, (long) ((gameTime - start + partialTick) * 50.0));
    }

    /** As the window asks (TelevisionPayload's actions), at this game time; by: who's holding it. */
    public TabletState apply(int action, String text, long value, long now, String by, RandomSource random) {
        UUID i = id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0 ? UUID.randomUUID() : id;
        long pos = position(now, 0.0F);
        return switch (action) {
            case TelevisionPayload.CHOOSE -> {
                String s = text.trim();
                yield new TabletState(i, s.length() > TelevisionBlockEntity.MAX_SOURCE ? s.substring(0, TelevisionBlockEntity.MAX_SOURCE) : s, by, now, false, 0,
                        volume, loop, 0, shuffle, shuffleSeed, shuffleFirst);
            }
            case TelevisionPayload.PLAY -> !paused ? this : new TabletState(i, source, chosenBy, now - at / 50, false, at, volume, loop, item, shuffle,
                    shuffleSeed, shuffleFirst);
            case TelevisionPayload.PAUSE -> paused ? this : new TabletState(i, source, chosenBy, start, true, pos, volume, loop, item, shuffle, shuffleSeed,
                    shuffleFirst);
            case TelevisionPayload.STOP -> new TabletState(i, "", chosenBy, start, false, 0, volume, loop, 0, shuffle, shuffleSeed, shuffleFirst);
            case TelevisionPayload.SEEK -> {
                long ms = Math.max(0, value);
                yield paused ? new TabletState(i, source, chosenBy, start, true, ms, volume, loop, item, shuffle, shuffleSeed, shuffleFirst)
                        : new TabletState(i, source, chosenBy, now - ms / 50, false, at, volume, loop, item, shuffle, shuffleSeed, shuffleFirst);
            }
            case TelevisionPayload.VOLUME -> new TabletState(i, source, chosenBy, start, paused, at, (int) Math.max(0, Math.min(100, value)), loop, item,
                    shuffle, shuffleSeed, shuffleFirst);
            case TelevisionPayload.LOOP -> new TabletState(i, source, chosenBy, start, paused, at, volume, value != 0, item, shuffle, shuffleSeed,
                    shuffleFirst);
            case TelevisionPayload.GOTO -> (int) (value >> 32) != item || source.isEmpty() ? this
                    : new TabletState(i, source, chosenBy, now, false, 0, volume, loop, (int) value, shuffle, shuffleSeed, shuffleFirst);
            case TelevisionPayload.SHUFFLE -> {
                int newItem;
                try {
                    newItem = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    yield this;
                }
                boolean on = (value & 1) != 0;
                yield new TabletState(i, source, chosenBy, start, paused, at, volume, loop, newItem, on, on ? random.nextLong() : shuffleSeed,
                        on ? (int) (value >> 1) : shuffleFirst);
            }
            default -> this;
        };
    }

    /** It, seen from a world at its game time (what a player follows). */
    public TvState view(net.minecraft.world.level.Level level) {
        TabletState s = this;
        return new TvState() {
            public String source() { return s.source; }
            public String chosenBy() { return s.chosenBy; }
            public long position(float pt) { return s.position(level.getGameTime(), pt); }
            public boolean paused() { return s.paused; }
            public int volume() { return s.volume; }
            public boolean loop() { return s.loop; }
            public int item() { return s.item; }
            public boolean shuffle() { return s.shuffle; }
            public long shuffleSeed() { return s.shuffleSeed; }
            public int shuffleFirst() { return s.shuffleFirst; }
        };
    }
}
