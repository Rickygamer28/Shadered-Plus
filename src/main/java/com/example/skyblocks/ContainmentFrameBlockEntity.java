package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** A containment frame: the singularity whose chamber it's part of (none: not formed). */
public class ContainmentFrameBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos master;

    public ContainmentFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTAINMENT_FRAME.get(), pos, state);
    }

    @Nullable
    public BlockPos master() {
        return master;
    }

    public void setMaster(@Nullable BlockPos master) {
        if (master == null ? this.master != null : !master.equals(this.master)) {
            this.master = master == null ? null : master.immutable();
            setChanged();
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
}
