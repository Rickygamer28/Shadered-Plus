package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Only there so the disco ball gets a renderer (client/DiscoBallRenderer); it keeps nothing. */
public class DiscoBallBlockEntity extends BlockEntity {
    public DiscoBallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISCO_BALL.get(), pos, state);
    }

    /** Its light spots reach this far (DiscoBallRenderer's range; 1.20.1: the block entity says). */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(24.0);
    }
}
