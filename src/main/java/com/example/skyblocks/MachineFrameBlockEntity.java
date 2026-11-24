package com.example.skyblocks;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

/** A machine's frame: the machine it's part of (none: not formed) - its energy and items reached through it. */
public class MachineFrameBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos master;

    public MachineFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_FRAME.get(), pos, state);
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

    @Nullable
    private MachineBlockEntity machine() {
        if (master != null && level != null && level.getBlockEntity(master) instanceof MachineBlockEntity m && m.size() > 0) {
            return m;
        }
        return null;
    }

    @Nullable
    public IEnergyStorage energy() {
        MachineBlockEntity m = machine();
        return m == null ? null : m.energyHandler();
    }

    @Nullable
    public IItemHandler items() {
        MachineBlockEntity m = machine();
        return m == null ? null : m.itemHandler();
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
