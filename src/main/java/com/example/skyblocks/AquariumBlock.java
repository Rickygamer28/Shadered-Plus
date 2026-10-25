package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An aquarium: touching aquarium blocks make one glass tank of water with fish swimming in it, drawn by
 * client/AquariumRenderer (up close; further away the plain glass-and-water texture).
 */
public class AquariumBlock extends TransparentBlock {
    /** More of the same tank above / below (the plain texture then has no light surface / no sand on that edge). */
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private final boolean ocean;

    public AquariumBlock(Properties properties) {
        this(properties, false);
    }

    /** ocean: the ocean aquarium (big sea animals, for giant tanks). */
    public AquariumBlock(Properties properties, boolean ocean) {
        super(properties);
        this.ocean = ocean;
        registerDefaultState(stateDefinition.any().setValue(UP, false).setValue(DOWN, false));
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

    public boolean ocean() {
        return ocean;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) { // which fish to show (a setting on this computer: the fish are only drawn there)
            com.example.skyblocks.client.ClientAccess.openAquarium(ocean); // (client-only class, never loaded on a server)
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(ocean ? "tooltip.skyblocks.ocean_aquarium" : "tooltip.skyblocks.aquarium").withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState next, Direction side) {
        return next.is(this) || super.skipRendering(state, next, side); // (one tank: no glass between its blocks)
    }
}
