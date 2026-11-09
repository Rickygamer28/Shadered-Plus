package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A spotlight: its colour and on / off; on the client, where its lamp points now (turning smoothly). */
public class SpotlightBlockEntity extends BlockEntity {
    private int colour = 0xFFF4D8;
    private boolean on = true;
    public float yaw, pitch = -0.6F;
    public long turnedAt;

    public SpotlightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPOTLIGHT.get(), pos, state);
    }

    public int colour() {
        return colour;
    }

    public boolean on() {
        return on;
    }

    public void setColour(int c) {
        colour = c;
        changed();
    }

    public void setOn(boolean o) {
        on = o;
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
        tag.putInt("colour", colour);
        tag.putBoolean("off", !on);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        colour = tag.contains("colour") ? tag.getInt("colour") : 0xFFF4D8;
        on = !tag.getBoolean("off");
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
