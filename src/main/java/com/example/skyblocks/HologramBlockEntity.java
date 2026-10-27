package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The hologram projector: the item it shows (a copy - the item isn't used up) and the hologram's colour. */
public class HologramBlockEntity extends GlowColourBlockEntity {
    private ItemStack shown = ItemStack.EMPTY;

    public HologramBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOLOGRAM.get(), pos, state, 0x45E0FF);
    }

    public ItemStack shown() {
        return shown;
    }

    public void setShown(ItemStack stack) {
        shown = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!shown.isEmpty()) {
            tag.put("shown", shown.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        shown = tag.contains("shown") ? ItemStack.of(tag.getCompound("shown")) : ItemStack.EMPTY;
    }

    /** What its renderer draws (1.20.1: the block entity says, not the renderer). */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(1.5);
    }
}
