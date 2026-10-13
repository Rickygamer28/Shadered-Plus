package com.example.skyblocks.island;

import com.example.skyblocks.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Remembers the Island Scanner's settings (synced, so the menu opens with them). */
public class IslandScannerBlockEntity extends BlockEntity {
    private int size = 15;
    private int above = 12;
    private int depth = 6;
    /** Game time of the last scan (not saved): scans are limited to one per second. */
    private long lastScan = Long.MIN_VALUE;

    public IslandScannerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ISLAND_SCANNER.get(), pos, state);
    }

    public int size() {
        return size;
    }

    public int above() {
        return above;
    }

    public int depth() {
        return depth;
    }

    /** True (and starts the cooldown) if a scan may run now. */
    boolean tryStartScan(long gameTime) {
        if (gameTime - lastScan < 20) {
            return false;
        }
        lastScan = gameTime;
        return true;
    }

    void setSettings(int size, int above, int depth) {
        this.size = IslandScan.clampSize(size);
        this.above = IslandScan.clampAbove(above);
        this.depth = IslandScan.clampDepth(depth);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("size")) {
            size = IslandScan.clampSize(tag.getInt("size"));
            above = IslandScan.clampAbove(tag.getInt("above"));
            depth = IslandScan.clampDepth(tag.getInt("depth"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("size", size);
        tag.putInt("above", above);
        tag.putInt("depth", depth);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
