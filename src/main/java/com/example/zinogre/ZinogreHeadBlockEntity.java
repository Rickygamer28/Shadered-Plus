package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The placed head (no data: the look comes from the block). */
public class ZinogreHeadBlockEntity extends BlockEntity {
    /** Client only: the renderer's model copy for this head. */
    public Object clientState;

    public ZinogreHeadBlockEntity(BlockPos pos, BlockState state) {
        super(ZinogreMod.HEAD_BLOCK_ENTITY.get(), pos, state);
    }
}
