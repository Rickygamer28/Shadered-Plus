package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A lava lamp pillar block: its wax's and its liquid's colours (the pillar's bottom block's are the ones shown). */
public class LavaPillarBlockEntity extends BlockEntity {
    public static final int DEFAULT_WAX = 0xFF5A1F, DEFAULT_LIQUID = 0x4A1060;
    private int wax = DEFAULT_WAX, liquid = DEFAULT_LIQUID;
    /** On (its wax moving, glowing) or off; RGB: its wax cycling through the rainbow (as a lava lamp's). */
    private boolean lit = true, rgb;

    public LavaPillarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAVA_PILLAR.get(), pos, state);
    }

    public int wax() {
        return wax;
    }

    public int liquid() {
        return liquid;
    }

    public void setWax(int c) {
        rgb = false;
        wax = c & 0xFFFFFF;
        changed();
    }

    public boolean lit() {
        return lit;
    }

    public boolean rgb() {
        return rgb;
    }

    public void setLit(boolean on) {
        lit = on;
        changed();
    }

    public void setRgb(boolean on) {
        rgb = on;
        changed();
    }

    public void setLiquid(int c) {
        liquid = c & 0xFFFFFF;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("wax", wax);
        tag.putInt("liquid", liquid);
        tag.putBoolean("off", !lit);
        tag.putBoolean("rgb", rgb);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wax = tag.contains("wax") ? tag.getInt("wax") : DEFAULT_WAX;
        liquid = tag.contains("liquid") ? tag.getInt("liquid") : DEFAULT_LIQUID;
        lit = !tag.getBoolean("off");
        rgb = tag.getBoolean("rgb");
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
