package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A party laser: on or off (a redstone signal turns it on too), its pattern, its colour (-1: rainbow). */
public class LaserProjectorBlockEntity extends BlockEntity {
    public static final String[] PATTERNS = {"fan", "cone", "tunnel", "scan", "music"};
    private boolean on = true, powered;
    private int pattern;
    private int colour = -1;

    public LaserProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_PROJECTOR.get(), pos, state);
    }

    /** Shining now: switched on, or powered. */
    public boolean shining() {
        return on || powered;
    }

    public boolean on() {
        return on;
    }

    public int pattern() {
        return pattern;
    }

    public int colour() {
        return colour;
    }

    public void setOn(boolean v) {
        on = v;
        changed();
    }

    public void setPowered(boolean v) {
        if (v != powered) {
            powered = v;
            changed();
        }
    }

    public void nextPattern() {
        pattern = (pattern + 1) % PATTERNS.length;
        changed();
    }

    public void setColour(int c) {
        colour = c;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("on", on);
        tag.putBoolean("powered", powered);
        tag.putInt("pattern", pattern);
        tag.putInt("colour", colour);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = !tag.contains("on") || tag.getBoolean("on");
        powered = tag.getBoolean("powered");
        pattern = Math.floorMod(tag.getInt("pattern"), PATTERNS.length);
        colour = tag.contains("colour") ? tag.getInt("colour") : -1;
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
