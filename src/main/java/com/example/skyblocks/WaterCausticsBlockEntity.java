package com.example.skyblocks;

import com.example.skyblocks.island.ShaderedSkyItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

/**
 * What is under the water of a water caustics block: nothing (just water), a block, or a Shadered sky block (any sky,
 * Shadered's or ours). Keeps the item that was put in, given back as it was.
 */
public class WaterCausticsBlockEntity extends BlockEntity {
    /** For client/WaterCausticsModel: the block under the water. */
    public static final ModelProperty<BlockState> INSIDE = new ModelProperty<>();
    /** For client/WaterCausticsModel: a sky is under the water (drawn by Shadered; the model only adds the light). */
    public static final ModelProperty<Boolean> SKY = new ModelProperty<>();

    private ItemStack held = ItemStack.EMPTY;

    public WaterCausticsBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WATER_CAUSTICS.get(), pos, state);
    }

    public ItemStack held() {
        return held;
    }

    /** The Shadered sky under the water, or null. */
    @Nullable
    public ShaderedSkyItems.Sky sky() {
        return held.isEmpty() ? null : ShaderedSkyItems.sky(held);
    }

    /** The block under the water (not a sky), or null. */
    @Nullable
    public BlockState inside() {
        if (held.isEmpty() || sky() != null || !(held.getItem() instanceof BlockItem item)) {
            return null;
        }
        return item.getBlock().defaultBlockState();
    }

    public void setHeld(ItemStack stack) {
        held = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!held.isEmpty()) {
            tag.put("held", held.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ItemStack before = held;
        if (tag.contains("held")) {
            held = ItemStack.of(tag.getCompound("held"));
        } else if (tag.contains("inside")) { // (the first version kept just a block state)
            BlockState state = NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("inside"));
            held = state.isAir() ? ItemStack.EMPTY : new ItemStack(state.getBlock().asItem());
        } else {
            held = ItemStack.EMPTY;
        }
        if (level != null && level.isClientSide && !ItemStack.isSameItemSameTags(before, held)) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE); // redraw the chunk
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
        if (!pkt.getTag().contains("held")) {
            load(new CompoundTag()); // (taken out: the tag has nothing under the water)
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        requestModelDataUpdate();
    }

    @Override
    public ModelData getModelData() {
        if (sky() != null) {
            return ModelData.builder().with(SKY, true).build();
        }
        BlockState inside = inside();
        return inside == null ? ModelData.EMPTY : ModelData.builder().with(INSIDE, inside).build();
    }
}
