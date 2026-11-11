package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Which photo a frame holds. */
public class PhotoFrameBlockEntity extends BlockEntity {
    @Nullable
    private ModComponents.Photo photo;
    /** Placed from a frame item (it gives the frame back; a frame made straight from a photo goes with its photo). */
    private boolean framed;

    public PhotoFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHOTO_FRAME.get(), pos, state);
    }

    @Nullable
    public ModComponents.Photo photo() {
        return photo;
    }

    public boolean framed() {
        return framed;
    }

    public void setFramed(boolean framed) {
        this.framed = framed;
        setChanged();
    }

    /** The photo gone with its frame (taken out of a frame that came with it): nothing to give back when it's removed. */
    public void forget() {
        photo = null;
        framed = false;
    }

    public ItemStack photoStack() {
        return stack();
    }

    public void setPhoto(@Nullable ModComponents.Photo photo) {
        this.photo = photo;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public ItemStack stack() {
        ItemStack s = new ItemStack(ModItems.PHOTO.get());
        if (photo != null) {
            s.set(ModComponents.PHOTO.get(), photo);
        }
        return s;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (photo != null) {
            tag.putInt("photo", photo.id());
            tag.putString("by", photo.by());
            tag.putLong("day", photo.day());
        }
        tag.putBoolean("framed", framed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        photo = tag.contains("photo") ? new ModComponents.Photo(tag.getInt("photo"), tag.getString("by"), tag.getLong("day")) : null;
        framed = tag.getBoolean("framed");
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
