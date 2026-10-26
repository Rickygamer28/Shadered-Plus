package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * An aquarium in plain Minecraft style: touching blocks make one glass tank of the game's own water, sand on the
 * bottom; client/VanillaAquariumRenderer puts the game's own fish and plants in it (tropical fish, cod, salmon,
 * pufferfish, axolotls, tadpoles; the ocean one dolphins, sea turtles, squid and glow squid too, and coral).
 */
public class VanillaAquariumBlock extends TransparentBlock {
    /** More of the same tank above (no water surface here) / below (no sand here). */
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private final boolean ocean;

    public VanillaAquariumBlock(Properties properties, boolean ocean) {
        super(properties);
        this.ocean = ocean;
        registerDefaultState(stateDefinition.any().setValue(UP, false).setValue(DOWN, false));
    }

    public boolean ocean() {
        return ocean;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return defaultBlockState().setValue(UP, level.getBlockState(pos.above()).is(this)).setValue(DOWN, level.getBlockState(pos.below()).is(this));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        if (dir == Direction.UP) return state.setValue(UP, next.is(this));
        if (dir == Direction.DOWN) return state.setValue(DOWN, next.is(this));
        return state;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState next, Direction side) {
        return next.is(this) || super.skipRendering(state, next, side); // (one tank: no glass or water between its blocks)
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId().replace("block.", "tooltip.")).withStyle(ChatFormatting.GRAY));
    }
}
