package com.example.skyblocks;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An aquarium: touching aquarium blocks make one glass tank of water with fish swimming in it, drawn by
 * client/AquariumRenderer (up close; further away the plain glass-and-water texture).
 */
public class AquariumBlock extends TransparentBlock {
    public AquariumBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState next, Direction side) {
        return next.is(this) || super.skipRendering(state, next, side); // (one tank: no glass between its blocks)
    }
}
