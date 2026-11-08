package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** A containment frame: the singularity whose chamber it's part of (none: not formed). */
public class ContainmentFrameBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos master;

    public ContainmentFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTAINMENT_FRAME.get(), pos, state);
    }

    protected ContainmentFrameBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Nullable
    public BlockPos master() {
        return master;
    }

    public void setMaster(@Nullable BlockPos master) {
        if (master == null ? this.master != null : !master.equals(this.master)) {
            this.master = master == null ? null : master.immutable();
            setChanged();
            if (level != null && !level.isClientSide) { // (the client too: a display frame shows its singularity's)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Its singularity's items (hoppers, pipes), while the chamber is formed. */
    @Nullable
    public IItemHandler handler() {
        if (master != null && level != null && level.getBlockEntity(master) instanceof SingularityBlockEntity s && s.chamber() > 0) {
            return s.handler;
        }
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (master != null) {
            tag.putLong("master", master.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        master = tag.contains("master") ? BlockPos.of(tag.getLong("master")) : null;
    }

    /** Its singularity, while the chamber is formed. */
    @Nullable
    public SingularityBlockEntity singularity() {
        if (master != null && level != null && level.getBlockEntity(master) instanceof SingularityBlockEntity s && s.chamber() > 0) {
            return s;
        }
        return null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** 1.20.1: hoppers and pipes find this through Forge's capabilities (1.21.1 registers it in an event). */
    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap,
                                                                             @org.jetbrains.annotations.Nullable net.minecraft.core.Direction side) {
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
            net.minecraftforge.items.IItemHandler h = handler();
            if (h != null) {
                return net.minecraftforge.common.util.LazyOptional.of(() -> h).cast();
            }
        }
        return super.getCapability(cap, side);
    }
}
