package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A glitch block (GlitchBlock): whether its glitch zone's on (right-click; a redstone signal turns it on too). */
public class GlitchBlockEntity extends BlockEntity {
    private boolean zone;

    public GlitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GLITCH.get(), pos, state);
    }

    /** Whether the area round it glitches: switched on, or powered. */
    public boolean zone() {
        return zone || getBlockState().getValue(GlitchBlock.POWERED);
    }

    public boolean switchedOn() {
        return zone;
    }

    public void toggle() {
        zone = !zone;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("zone", zone);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        zone = tag.getBoolean("zone");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("zone", zone);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
