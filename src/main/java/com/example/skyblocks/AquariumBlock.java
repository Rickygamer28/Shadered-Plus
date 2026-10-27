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
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An aquarium: touching aquarium blocks make one glass tank of water with fish swimming in it, drawn by
 * client/AquariumRenderer (up close; further away the plain glass-and-water texture).
 */
public class AquariumBlock extends AbstractGlassBlock {
    /** More of the same tank above / below (the plain texture then has no light surface / no sand on that edge). */
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private final boolean ocean;
    private final boolean deep;

    public AquariumBlock(Properties properties) {
        this(properties, false);
    }

    /** ocean: the ocean aquarium (big sea animals, for giant tanks). */
    public AquariumBlock(Properties properties, boolean ocean) {
        this(properties, ocean, false);
    }

    /** deep: the deep sea aquarium (always dark: anglerfish, lanternfish, glowing jellyfish and squid). */
    public AquariumBlock(Properties properties, boolean ocean, boolean deep) {
        super(properties);
        this.ocean = ocean;
        this.deep = deep;
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

    public boolean deep() {
        return deep;
    }

    protected InteractionResult useWithoutItem121(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) { // which fish to show (a setting on this computer: the fish are only drawn there)
            com.example.skyblocks.client.ClientAccess.openAquarium(ocean, deep); // (client-only class, never loaded on a server)
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(deep ? "tooltip.skyblocks.deep_sea_aquarium" : ocean ? "tooltip.skyblocks.ocean_aquarium" : "tooltip.skyblocks.aquarium")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState next, Direction side) {
        return next.is(this) || super.skipRendering(state, next, side); // (one tank: no glass between its blocks)
    }

    protected com.example.skyblocks.compat.ItemInteractionResult useItemOn121(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        return com.example.skyblocks.compat.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 1.20.1: one use() - an item first (useItemOn121), then the empty-hand use (useWithoutItem121), as 1.21 does. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                                      net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                                      net.minecraft.world.phys.BlockHitResult hit) {
        com.example.skyblocks.compat.ItemInteractionResult r = useItemOn121(player.getItemInHand(hand), state, level, pos, player, hand, hit);
        if (r != com.example.skyblocks.compat.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return r.result();
        }
        return hand == net.minecraft.world.InteractionHand.MAIN_HAND ? useWithoutItem121(state, level, pos, player, hit) : net.minecraft.world.InteractionResult.PASS;
    }
}
