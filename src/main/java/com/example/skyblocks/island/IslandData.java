package com.example.skyblocks.island;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A scanned sky island (the value of the skyblocks:island item component and of the Sky Island block entity).
 * Immutable; the content is an NBT compound written by IslandScan:
 * <ul>
 *   <li>size, height: the island is size x height x size blocks; ground: the layer the scanner stood on</li>
 *   <li>palette: list of block states; blocks: one palette index per block, index = (y * size + z) * size + x,
 *       as a byte array (palette of up to 256) or an int array</li>
 *   <li>biomes: list of biome ids; biome_map: one biome index per column (z * size + x)</li>
 *   <li>entities: list of {entity: saved entity, x, y, z: position inside the island}</li>
 * </ul>
 */
public final class IslandData {
    public static final Codec<IslandData> CODEC = CompoundTag.CODEC.xmap(IslandData::new, IslandData::tag);
    public static final StreamCodec<ByteBuf, IslandData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(IslandData::new, IslandData::tag);

    private final CompoundTag tag;
    private final int hash;

    public IslandData(CompoundTag tag) {
        this.tag = tag.copy();
        this.hash = this.tag.hashCode();
    }

    /** A copy of the data. */
    public CompoundTag tag() {
        return tag.copy();
    }

    /** Read-only access without copying (callers must not change it). */
    public CompoundTag view() {
        return tag;
    }

    public int size() {
        return tag.getInt("size");
    }

    public int height() {
        return tag.getInt("height");
    }

    public int mobs() {
        return tag.getInt("mobs");
    }

    @Override
    public boolean equals(Object o) {
        return o == this || (o instanceof IslandData other && hash == other.hash && tag.equals(other.tag));
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
