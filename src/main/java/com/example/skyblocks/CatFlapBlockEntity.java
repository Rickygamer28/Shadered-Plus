package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

/** What block a cat flap's built of (null: oak planks). */
public class CatFlapBlockEntity extends BlockEntity {
    public static final ModelProperty<BlockState> MATERIAL = new ModelProperty<>();
    @Nullable
    private BlockState material;

    public CatFlapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAT_FLAP.get(), pos, state);
    }

    @Nullable
    public BlockState material() {
        return material;
    }

    public void setMaterial(@Nullable BlockState state) {
        material = state;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (material != null) {
            tag.put("material", NbtUtils.writeBlockState(material));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        BlockState had = material;
        material = tag.contains("material") ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("material")) : null;
        if (level != null && level.isClientSide && had != material) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE); // (redraw the chunk)
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        tag.putBoolean("flap", true); // (never empty: an empty update is skipped)
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        requestModelDataUpdate();
    }

    @Override
    public ModelData getModelData() {
        return material == null ? ModelData.EMPTY : ModelData.builder().with(MATERIAL, material).build();
    }
}
