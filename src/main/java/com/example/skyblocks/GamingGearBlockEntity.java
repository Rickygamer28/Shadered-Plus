package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Gaming gear (only so its lights can be drawn: client/GamingGearRenderer). */
public class GamingGearBlockEntity extends BlockEntity {
    public GamingGearBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GAMING_GEAR.get(), pos, state);
    }
}
