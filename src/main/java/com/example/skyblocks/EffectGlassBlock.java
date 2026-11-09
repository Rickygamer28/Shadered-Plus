package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Effect Glass: the effect block, see-through - its effect glows in the glass, the dark parts clear (client/
 * EffectBlockRenderer). Joins effect blocks and other effect glass like they join each other.
 */
public class EffectGlassBlock extends EffectBlock {
    public static final MapCodec<EffectGlassBlock> CODEC = simpleCodec(EffectGlassBlock::new);

    public EffectGlassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.getBlock() instanceof EffectBlock || super.skipRendering(state, adjacent, side);
    }
}
