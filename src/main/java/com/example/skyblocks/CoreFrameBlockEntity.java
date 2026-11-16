package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** A core frame: the power core whose chamber it's part of (none: not formed). */
public class CoreFrameBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos master;

    public CoreFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CORE_FRAME.get(), pos, state);
    }

    @Nullable
    public BlockPos master() {
        return master;
    }

    public void setMaster(@Nullable BlockPos master) {
        if (master == null ? this.master != null : !master.equals(this.master)) {
            this.master = master == null ? null : master.immutable();
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Its core's energy (cables), while the chamber is formed. */
    @Nullable
    public IEnergyStorage handler() {
        if (master != null && level != null && level.getBlockEntity(master) instanceof PowerCoreBlockEntity w && w.chamber() > 0) {
            return w.handler;
        }
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (master != null) {
            tag.putLong("master", master.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        master = tag.contains("master") ? BlockPos.of(tag.getLong("master")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
