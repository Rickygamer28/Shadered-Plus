package com.example.skyblocks;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A block that shows one sky (the Sky Rift, the Sky Beacon): which one, by its name ("sky"). */
public class SkyHolderBlockEntity extends BlockEntity {
    private String sky = "SPACE";
    /** (client: how high the beacon's beam goes, and when that was last looked at) */
    public int beamTop = Integer.MIN_VALUE;
    public long beamChecked = Long.MIN_VALUE;

    public SkyHolderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static SkyHolderBlockEntity rift(BlockPos pos, BlockState state) {
        return new SkyHolderBlockEntity(ModBlockEntities.SKY_RIFT.get(), pos, state);
    }

    public static SkyHolderBlockEntity beacon(BlockPos pos, BlockState state) {
        return new SkyHolderBlockEntity(ModBlockEntities.SKY_BEACON.get(), pos, state);
    }

    @Nullable
    public SkyblockType sky() {
        try {
            return SkyblockType.valueOf(sky);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setSky(SkyblockType type) {
        sky = type.name();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("sky", sky);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("sky")) {
            sky = tag.getString("sky");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("sky", sky);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
