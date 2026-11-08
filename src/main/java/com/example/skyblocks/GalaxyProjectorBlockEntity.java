package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The galaxy projector (only so it has a renderer: client/GalaxyProjectorRenderer; its settings are in its state). */
public class GalaxyProjectorBlockEntity extends BlockEntity {
    public GalaxyProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GALAXY_PROJECTOR.get(), pos, state);
    }

    /** What its renderer draws (1.20.1: the block entity says, not the renderer). */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(9.0); // (its light's reach, and a block)
    }
}
