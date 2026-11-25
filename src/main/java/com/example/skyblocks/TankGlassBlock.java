package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The whirlpool tank's glass: in a formed tank, clear and seamless - one window from rim to rim. */
public class TankGlassBlock extends TransparentBlock {
    public static final MapCodec<TankGlassBlock> CODEC = simpleCodec(TankGlassBlock::new);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public TankGlassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    public MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }
}
