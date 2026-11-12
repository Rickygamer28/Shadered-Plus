package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The sky in a sky lantern: Shadered's sky type and pass (by name). */
public class SkyLanternBlockEntity extends BlockEntity {
    private String sky = "SPACE", pass = "NORMAL";

    public SkyLanternBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKY_LANTERN.get(), pos, state);
    }

    public String sky() {
        return sky;
    }

    public String pass() {
        return pass;
    }

    public void setSky(String sky, String pass) {
        this.sky = sky;
        this.pass = pass;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("sky", sky);
        tag.putString("pass", pass);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("sky")) {
            sky = tag.getString("sky");
            pass = tag.contains("pass") ? tag.getString("pass") : "NORMAL";
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
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
