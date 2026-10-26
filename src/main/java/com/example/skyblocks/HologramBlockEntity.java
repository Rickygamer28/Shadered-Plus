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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!shown.isEmpty()) {
            tag.put("shown", shown.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        shown = tag.contains("shown") ? ItemStack.parse(registries, tag.getCompound("shown")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
    }
}
