package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Every part of a brick hearth: the stone and the wood it is made of (set by right-clicking it with a block; shown by
 * client/HearthModel). The main part also keeps the flame's colour.
 */
public class BrickHearthBlockEntity extends GlowColourBlockEntity {
    public static final ModelProperty<BlockState> STONE = new ModelProperty<>();
    public static final ModelProperty<BlockState> WOOD = new ModelProperty<>();
    @Nullable private BlockState stone, wood;

    public BrickHearthBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BRICK_HEARTH.get(), pos, state, 0xFF8A1E);
    }

    @Nullable public BlockState stone() { return stone; }
    @Nullable public BlockState wood() { return wood; }

    public void setMaterials(@Nullable BlockState stone, @Nullable BlockState wood) {
        this.stone = stone;
        this.wood = wood;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (stone != null) tag.put("stone", NbtUtils.writeBlockState(stone));
        if (wood != null) tag.put("wood", NbtUtils.writeBlockState(wood));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        BlockState s0 = stone, w0 = wood;
        var blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup();
        stone = tag.contains("stone") ? NbtUtils.readBlockState(blocks, tag.getCompound("stone")) : null;
        wood = tag.contains("wood") ? NbtUtils.readBlockState(blocks, tag.getCompound("wood")) : null;
        if (level != null && level.isClientSide && (s0 != stone || w0 != wood)) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE); // (redraw the chunk)
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        requestModelDataUpdate();
    }

    @Override
    public ModelData getModelData() {
        ModelData.Builder b = ModelData.builder();
        if (stone != null) b.with(STONE, stone);
        if (wood != null) b.with(WOOD, wood);
        return b.end();
    }
}
