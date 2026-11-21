package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A train station's name (shown on its board, client/StationRenderer; set in client/StationScreen). */
public class StationBlockEntity extends SimpleRenderedBlockEntity {
    private String name = "";

    public StationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STATION.get(), pos, state);
    }

    public String name() {
        return name.isEmpty() ? "Station " + Math.floorMod(worldPosition.getX() * 31 + worldPosition.getZ() * 17, 100) : name;
    }

    public void setName(String n) {
        name = n.length() > 24 ? n.substring(0, 24) : n;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        register();
    }

    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        register();
    }

    /** Known to trains (TrainRoute): placed, or loaded. */
    private void register() {
        if (level != null) {
            synchronized (TrainRoute.class) {
                (level.isClientSide ? TrainRoute.CLIENT : TrainRoute.SERVER).add(this);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        synchronized (TrainRoute.class) {
            TrainRoute.CLIENT.remove(this);
            TrainRoute.SERVER.remove(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        synchronized (TrainRoute.class) {
            TrainRoute.CLIENT.remove(this);
            TrainRoute.SERVER.remove(this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("name", name);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = tag.getString("name");
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
