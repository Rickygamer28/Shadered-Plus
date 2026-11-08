package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A small fish tank (fishbowl, tall cylinder tank, slim wall tank): which fish live in it (one of three sets). */
public class SmallTankBlockEntity extends BlockEntity {
    public static final int VARIANTS = 3;
    private int variant;

    public SmallTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMALL_TANK.get(), pos, state);
    }

    public int variant() {
        return variant;
    }

    public void cycle() {
        variant = (variant + 1) % VARIANTS;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("variant", variant);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        variant = Math.floorMod(tag.getInt("variant"), VARIANTS);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** What its renderer draws (1.20.1: the block entity says, not the renderer). */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(getBlockPos()).expandTowards(0, SmallTankBlock.MAX, 0).inflate(SmallTankBlock.MAX, 0, SmallTankBlock.MAX);
    }
}
