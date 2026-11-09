package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** A block entity with nothing of its own - only so its block has a renderer (waterfall, stage); a little client state. */
public class SimpleRenderedBlockEntity extends BlockEntity {
    /** Client only: for its renderer (when it last made particles ...). */
    public long clientTick;
    public float clientA, clientB;

    public SimpleRenderedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
