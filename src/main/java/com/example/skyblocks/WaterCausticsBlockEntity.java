package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

/** Which block is under the water of a water caustics block (null: just water). */
public class WaterCausticsBlockEntity extends BlockEntity {
    /** For client/WaterCausticsModel. */
    public static final ModelProperty<BlockState> INSIDE = new ModelProperty<>();

    @Nullable
    private BlockState inside;

    public WaterCausticsBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WATER_CAUSTICS.get(), pos, state);
    }

    @Nullable
    public BlockState inside() {
        return inside;
    }

    public void setInside(@Nullable BlockState state) {
        inside = state;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (inside != null) {
            tag.put("inside", NbtUtils.writeBlockState(inside));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        BlockState before = inside;
        inside = tag.contains("inside") ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("inside")) : null;
        if (inside != null && inside.isAir()) {
            inside = null;
        }
        if (level != null && level.isClientSide && before != inside) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE); // redraw the chunk
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        super.onDataPacket(net, pkt, registries);
        if (pkt.getTag().isEmpty() || !pkt.getTag().contains("inside")) {
            loadAdditional(new CompoundTag(), registries); // (taken out: the tag has nothing under the water)
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        requestModelDataUpdate();
    }

    @Override
    public ModelData getModelData() {
        return inside == null ? ModelData.EMPTY : ModelData.builder().with(INSIDE, inside).build();
    }
}
