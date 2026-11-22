package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A Sky Studio: which studio sky it last worked on (its screen and its orb show that one). */
public class SkyStudioBlockEntity extends BlockEntity {
    private int slot;

    public SkyStudioBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKY_STUDIO.get(), pos, state);
    }

    public int slot() {
        return slot;
    }

    public void setSlot(int slot) {
        if (slot != this.slot && slot >= 0 && slot < StudioSkies.SLOTS) {
            this.slot = slot;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("slot", slot);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        slot = Math.max(0, Math.min(StudioSkies.SLOTS - 1, tag.getInt("slot")));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("slot", slot);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
