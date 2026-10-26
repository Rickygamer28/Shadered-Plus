package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A koi pond (no data: it is here so client/KoiPondRenderer can draw its water). */
public class KoiPondBlockEntity extends BlockEntity {
    public KoiPondBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KOI_POND.get(), pos, state);
    }
}
