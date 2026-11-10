package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A neon tube space: for each side with a tube, which way it lies when it's on its own (0 none yet, 1 x, 2 y, 3 z). */
public class NeonTubeBlockEntity extends BlockEntity {
    private final byte[] axes = new byte[6];

    public NeonTubeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NEON_TUBE.get(), pos, state);
    }

    public boolean known(Direction face) {
        return axes[face.ordinal()] != 0;
    }

    @Nullable
    public Direction.Axis axis(Direction face) {
        int a = axes[face.ordinal()];
        return a == 0 ? null : Direction.Axis.values()[a - 1];
    }

    public void setAxis(Direction face, Direction.Axis axis) {
        axes[face.ordinal()] = (byte) (axis.ordinal() + 1);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByteArray("axes", axes);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        byte[] a = tag.getByteArray("axes");
        System.arraycopy(a, 0, axes, 0, Math.min(a.length, axes.length));
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
