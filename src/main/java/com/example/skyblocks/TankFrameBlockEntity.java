package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** A tank frame: the whirlpool whose tank it's part of (none: not formed). */
public class TankFrameBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos master;

    public TankFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANK_FRAME.get(), pos, state);
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

    /** Its whirlpool's fluids (buckets, pipes), while the tank is formed. */
    @Nullable
    public IFluidHandler handler() {
        if (master != null && level != null && level.getBlockEntity(master) instanceof WhirlpoolBlockEntity w && w.tank() > 0) {
            return w.handler;
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
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER) {
            net.minecraftforge.fluids.capability.IFluidHandler h = handler();
            if (h != null) {
                return net.minecraftforge.common.util.LazyOptional.of(() -> h).cast();
            }
        }
        return super.getCapability(cap, side);
    }
}
