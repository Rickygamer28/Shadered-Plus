package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A beat lamp (only so its light can be drawn: client/BeatRenderers.Lamp). */
public class BeatLampBlockEntity extends BlockEntity {
    public BeatLampBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BEAT_LAMP.get(), pos, state);
    }
}
