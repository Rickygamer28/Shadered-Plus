package com.example.skyblocks;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sky paint on the faces of blocks in the world (SkyBrushItem): per block, which sky each of its six faces shows (one
 * byte a face: the sky's number + 1, 0 none; packed in a long). Kept per dimension (saved by sky name, so skies added
 * later don't shift it), sent to players with each chunk they see and as it changes (SkyPaintStore holds the client's
 * copy; client/SkyLiquidRenderer draws it).
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID)
public final class SkyPaint extends SavedData {
    /** Per chunk: block (BlockPos.asLong) -> its faces. */
    private final Map<Long, Map<Long, Long>> chunks = new HashMap<>();

    public static SkyPaint get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(SkyPaint::new, SkyPaint::load, null), "skyblocks_sky_paint");
    }

    /** The face's sky in a packed long, or null. */
    public static SkyblockType skyOf(long faces, Direction face) {
        int b = (int) (faces >>> (face.ordinal() * 8)) & 0xFF;
        SkyblockType[] skies = SkyblockType.values();
        return b == 0 || b > skies.length ? null : skies[b - 1];
    }

    static long with(long faces, Direction face, SkyblockType sky) {
        long mask = 0xFFL << (face.ordinal() * 8);
        return (faces & ~mask) | (sky == null ? 0L : ((long) (sky.ordinal() + 1) << (face.ordinal() * 8)));
    }

    /** Paints (or with null, clears) one face; true if it changed. Sent to the players who see that chunk. */
    public boolean paint(ServerLevel level, BlockPos pos, Direction face, SkyblockType sky) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Map<Long, Long> chunk = chunks.computeIfAbsent(key, k -> new HashMap<>());
        long had = chunk.getOrDefault(pos.asLong(), 0L);
        long now = with(had, face, sky);
        if (now == had) {
            return false;
        }
        if (now == 0L) {
            chunk.remove(pos.asLong());
            if (chunk.isEmpty()) {
                chunks.remove(key);
            }
        } else {
            chunk.put(pos.asLong(), now);
        }
        setDirty();
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos), new Set(pos.asLong(), now));
        return true;
    }

    /** All a block's paint gone (it was broken). */
    public void clear(ServerLevel level, BlockPos pos) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Map<Long, Long> chunk = chunks.get(key);
        if (chunk != null && chunk.remove(pos.asLong()) != null) {
            if (chunk.isEmpty()) {
                chunks.remove(key);
            }
            setDirty();
            PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos), new Set(pos.asLong(), 0L));
        }
    }

    private static SkyPaint load(CompoundTag tag, HolderLookup.Provider registries) {
        SkyPaint p = new SkyPaint();
        Map<String, SkyblockType> byName = new HashMap<>();
        for (SkyblockType sky : SkyblockType.values()) {
            byName.put(sky.name(), sky);
        }
        ListTag names = tag.getList("skies", Tag.TAG_STRING);
        SkyblockType[] palette = new SkyblockType[names.size()];
        for (int i = 0; i < names.size(); i++) {
            palette[i] = byName.get(names.getString(i));
        }
        long[] pos = tag.getLongArray("pos");
        long[] faces = tag.getLongArray("faces");
        for (int i = 0; i < Math.min(pos.length, faces.length); i++) {
            long out = 0L;
            for (Direction d : Direction.values()) {
                int b = (int) (faces[i] >>> (d.ordinal() * 8)) & 0xFF;
                if (b > 0 && b <= palette.length && palette[b - 1] != null) {
                    out = with(out, d, palette[b - 1]);
                }
            }
            if (out != 0L) {
                BlockPos at = BlockPos.of(pos[i]);
                p.chunks.computeIfAbsent(ChunkPos.asLong(at.getX() >> 4, at.getZ() >> 4), k -> new HashMap<>()).put(pos[i], out);
            }
        }
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        // (by name: a face's byte is a number in this list + 1)
        ListTag names = new ListTag();
        for (SkyblockType sky : SkyblockType.values()) {
            names.add(net.minecraft.nbt.StringTag.valueOf(sky.name()));
        }
        int n = 0;
        for (Map<Long, Long> chunk : chunks.values()) {
            n += chunk.size();
        }
        long[] pos = new long[n], faces = new long[n];
        int i = 0;
        for (Map<Long, Long> chunk : chunks.values()) {
            for (Map.Entry<Long, Long> e : chunk.entrySet()) {
                pos[i] = e.getKey();
                faces[i++] = e.getValue();
            }
        }
        tag.put("skies", names);
        tag.putLongArray("pos", pos);
        tag.putLongArray("faces", faces);
        return tag;
    }

    // ---- keeping players up to date ----

    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        SkyPaint paint = get(event.getLevel());
        Map<Long, Long> chunk = paint.chunks.get(event.getPos().toLong());
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        long[] pos = new long[chunk.size()], faces = new long[chunk.size()];
        int i = 0;
        for (Map.Entry<Long, Long> e : chunk.entrySet()) {
            pos[i] = e.getKey();
            faces[i++] = e.getValue();
        }
        PacketDistributor.sendToPlayer(event.getPlayer(), new Chunk(event.getPos().toLong(), pos, faces));
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            get(level).clear(level, event.getPos());
        }
    }

    /** One block's paint (0: none). */
    public record Set(long pos, long faces) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "sky_paint_set"));
        public static final StreamCodec<ByteBuf, Set> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Set::pos, ByteBufCodecs.VAR_LONG, Set::faces, Set::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Set p, IPayloadContext context) {
            context.enqueueWork(() -> SkyPaintStore.set(p.pos, p.faces));
        }
    }

    /** A chunk's paint, as it's sent to a player. */
    public record Chunk(long chunk, long[] pos, long[] faces) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "sky_paint_chunk"));
        public static final StreamCodec<ByteBuf, Chunk> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeLong(p.chunk);
                    ByteBufCodecs.VAR_INT.encode(buf, p.pos.length);
                    for (int i = 0; i < p.pos.length; i++) {
                        buf.writeLong(p.pos[i]);
                        buf.writeLong(p.faces[i]);
                    }
                },
                buf -> {
                    long chunk = buf.readLong();
                    int n = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 1 << 16);
                    long[] pos = new long[n], faces = new long[n];
                    for (int i = 0; i < n; i++) {
                        pos[i] = buf.readLong();
                        faces[i] = buf.readLong();
                    }
                    return new Chunk(chunk, pos, faces);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Chunk p, IPayloadContext context) {
            context.enqueueWork(() -> SkyPaintStore.chunk(p.chunk, p.pos, p.faces));
        }
    }

    private SkyPaint() {}
}
