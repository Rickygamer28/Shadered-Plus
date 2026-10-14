package com.example.skyblocks.island;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A scanned sky island (kept on the Sky Island item as BlockEntityTag.island, so placing the item gives the block
 * entity its island, and in the Sky Island block entity).
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

    /** The island on a Sky Island item, or null. */
    @Nullable
    public static IslandData get(ItemStack stack) {
        CompoundTag be = stack.getTagElement("BlockEntityTag");
        return be != null && be.contains("island", net.minecraft.nbt.Tag.TAG_COMPOUND) ? new IslandData(be.getCompound("island")) : null;
    }

    /** Puts an island on a Sky Island item. */
    public static void set(ItemStack stack, IslandData island) {
        stack.getOrCreateTagElement("BlockEntityTag").put("island", island.tag());
    }

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

    /** Whether it knows where it was scanned (islands scanned before this was added don't). */
    public boolean hasOrigin() {
        return tag.contains("origin_x") && tag.contains("dimension");
    }

    /** Where the Island Scanner stood. */
    public net.minecraft.core.BlockPos origin() {
        return new net.minecraft.core.BlockPos(tag.getInt("origin_x"), tag.getInt("origin_y"), tag.getInt("origin_z"));
    }

    public String dimension() {
        return tag.getString("dimension");
    }

    /** In-game day it was scanned on, or -1 if unknown. */
    public long scannedDay() {
        return tag.contains("scanned_day") ? tag.getLong("scanned_day") : -1;
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
