package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A neon sign's text ('|' starts a new line), colour, how it glows (steady, flicker, pulse, rainbow) and its size. */
public class NeonSignBlockEntity extends BlockEntity {
    public static final int MAX_LENGTH = 40;
    public static final int STEADY = 0, FLICKER = 1, PULSE = 2, RAINBOW = 3, MODES = 4;
    public static final int MAX_SIZE = 6;

    private String text = "OPEN";
    private int colour = 0xFF3FD0;
    private int mode = STEADY;
    private int size = 2;

    public NeonSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NEON_SIGN.get(), pos, state);
    }

    public String text() { return text; }
    public int colour() { return colour; }
    public int mode() { return mode; }
    public int size() { return size; }

    public void setAll(String text, int colour, int mode, int size) {
        String t = text == null ? "" : text;
        this.text = t.length() > MAX_LENGTH ? t.substring(0, MAX_LENGTH) : t;
        this.colour = colour & 0xFFFFFF;
        this.mode = Math.floorMod(mode, MODES);
        this.size = Math.max(1, Math.min(MAX_SIZE, size));
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("text", text);
        tag.putInt("colour", colour);
        tag.putInt("mode", mode);
        tag.putInt("size", size);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("text")) {
            text = tag.getString("text");
            colour = tag.getInt("colour") & 0xFFFFFF;
            mode = Math.floorMod(tag.getInt("mode"), MODES);
            size = Math.max(1, Math.min(MAX_SIZE, tag.getInt("size")));
        }
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
