package com.example.skyblocks.island;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An island's blocks, for working out where its mobs can walk (both sides: the server decides where mobs go, the
 * client follows along). Same decoding, floor rule and bounds as the client's IslandView.
 */
public final class IslandGrid {
    public final int size, height;
    private final BlockState[] states;
    private final int[] bounds;

    private IslandGrid(int size, int height, BlockState[] states) {
        this.size = size;
        this.height = height;
        this.states = states;
        this.bounds = computeBounds();
    }

    /** Null if the data isn't a valid island. */
    public static IslandGrid decode(IslandData data) {
        CompoundTag tag = data.view();
        int size = tag.getInt("size");
        int height = tag.getInt("height");
        if (size <= 0 || height <= 0 || size > 64 || height > 128) {
            return null;
        }
        ListTag paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
        BlockState[] palette = new BlockState[Math.max(1, paletteTag.size())];
        palette[0] = Blocks.AIR.defaultBlockState();
        for (int i = 0; i < paletteTag.size(); i++) {
            palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i));
        }
        int count = size * height * size;
        int[] indices;
        if (tag.getTagType("blocks") == Tag.TAG_BYTE_ARRAY) {
            byte[] bytes = tag.getByteArray("blocks");
            indices = new int[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                indices[i] = bytes[i] & 0xFF;
            }
        } else {
            indices = tag.getIntArray("blocks");
        }
        BlockState[] states = new BlockState[count];
        for (int i = 0; i < count; i++) {
            int index = i < indices.length ? indices[i] : 0;
            states[i] = index >= 0 && index < palette.length ? palette[index] : palette[0];
        }
        return new IslandGrid(size, height, states);
    }

    public BlockState stateAt(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= size || y >= height || z >= size) {
            return Blocks.AIR.defaultBlockState();
        }
        return states[(y * size + z) * size + x];
    }

    /** Height a mob standing near (x, fromY, z) stands at, or NaN if there is no floor within reach. */
    public double floorBelow(double x, double fromY, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        int top = (int) Math.floor(fromY + 1.2);
        for (int y = Math.min(top, height - 1); y >= Math.max(0, (int) Math.floor(fromY) - 3); y--) {
            if (stateAt(ix, y, iz).blocksMotion()) {
                return y + 1;
            }
        }
        return Double.NaN;
    }

    private int[] computeBounds() {
        int minX = size, minY = height, minZ = size, maxX = -1, maxY = -1, maxZ = -1;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    if (!states[(y * size + z) * size + x].isAir()) {
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    }
                }
            }
        }
        return maxX < 0 ? new int[] {0, 0, 0, size - 1, height - 1, size - 1} : new int[] {minX, minY, minZ, maxX, maxY, maxZ};
    }

    /** The lowest layer with blocks (the pedestal shows the island from there up). */
    public int lowestLayer() {
        return bounds[1];
    }

    /** Centre of the blocks on x / z (the pedestal centres the island on it). */
    public float contentCenterX() {
        return (bounds[0] + bounds[3] + 1) / 2.0F;
    }

    public float contentCenterZ() {
        return (bounds[2] + bounds[5] + 1) / 2.0F;
    }
}
