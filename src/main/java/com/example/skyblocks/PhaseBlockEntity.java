package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A phase block's look: the block it looks like (air: its own sci-fi panel) and how it opens and shuts (MODES). Set
 * for its whole wall at once (PhasePayload, from client/PhaseScreen). On the client, when it last opened or shut.
 */
public class PhaseBlockEntity extends SimpleRenderedBlockEntity {
    /** burn, pixels, scan, hologram, ripple */
    public static final String[] MODES = {"burn", "pixels", "scan", "hologram", "ripple"};
    private BlockState disguise = Blocks.AIR.defaultBlockState();
    private int mode;

    public PhaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHASE_BLOCK.get(), pos, state);
    }

    public BlockState disguise() {
        return disguise;
    }

    public int mode() {
        return mode;
    }

    public void set(BlockState look, int mode) {
        this.disguise = look;
        this.mode = Math.floorMod(mode, MODES.length);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!disguise.isAir()) {
            tag.put("look", NbtUtils.writeBlockState(disguise));
        }
        tag.putInt("mode", mode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        disguise = tag.contains("look") ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("look"))
                : Blocks.AIR.defaultBlockState();
        mode = Math.floorMod(tag.getInt("mode"), MODES.length);
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
