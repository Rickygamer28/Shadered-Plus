package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A pond waterfall (its water drawn by client/PondWaterfallRenderer). */
public class PondWaterfallBlockEntity extends BlockEntity {
    public PondWaterfallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POND_WATERFALL.get(), pos, state);
    }
}
