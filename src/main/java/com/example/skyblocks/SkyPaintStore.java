package com.example.skyblocks;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * The client's copy of the sky paint (SkyPaint) of the chunks it sees: per chunk, block -> faces. The renderer
 * (client/SkyLiquidRenderer) draws it; {@link #version} changes with every change.
 */
public final class SkyPaintStore {
    private static final Map<Long, Map<Long, Long>> CHUNKS = new HashMap<>();
    public static long version;

    static void set(long pos, long faces) {
        BlockPos at = BlockPos.of(pos);
        long key = ChunkPos.asLong(at.getX() >> 4, at.getZ() >> 4);
        if (faces == 0L) {
            Map<Long, Long> chunk = CHUNKS.get(key);
            if (chunk != null) {
                chunk.remove(pos);
                if (chunk.isEmpty()) {
                    CHUNKS.remove(key);
                }
            }
        } else {
            CHUNKS.computeIfAbsent(key, k -> new HashMap<>()).put(pos, faces);
        }
        version++;
    }

    static void chunk(long chunk, long[] pos, long[] faces) {
        Map<Long, Long> map = new HashMap<>();
        for (int i = 0; i < pos.length; i++) {
            if (faces[i] != 0L) {
                map.put(pos[i], faces[i]);
            }
        }
        if (map.isEmpty()) {
            CHUNKS.remove(chunk);
        } else {
            CHUNKS.put(chunk, map);
        }
        version++;
    }

    /** A chunk's paint (block -> faces), or null. */
    public static Map<Long, Long> of(int chunkX, int chunkZ) {
        return CHUNKS.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** Forgets a chunk (unloaded on the client) or everything (the world left). */
    public static void forget(long chunk) {
        if (CHUNKS.remove(chunk) != null) {
            version++;
        }
    }

    public static void clear() {
        CHUNKS.clear();
        version++;
    }

    private SkyPaintStore() {}
}
