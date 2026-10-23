package com.example.skyblocks;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An aquarium: touching aquarium blocks make one glass tank of water with fish swimming in it, drawn by
 * client/AquariumRenderer (up close; further away the plain glass-and-water texture).
 */
public class AquariumBlock extends TransparentBlock {
    private final boolean ocean;

    public AquariumBlock(Properties properties) {
        this(properties, false);
    }

    /** ocean: the ocean aquarium (big sea animals, for giant tanks). */
    public AquariumBlock(Properties properties, boolean ocean) {
        super(properties);
        this.ocean = ocean;
    }

    public boolean ocean() {
        return ocean;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState next, Direction side) {
        return next.is(this) || super.skipRendering(state, next, side); // (one tank: no glass between its blocks)
    }
}
