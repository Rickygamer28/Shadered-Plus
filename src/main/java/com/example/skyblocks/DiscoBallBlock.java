package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A disco ball: a spinning mirror ball throwing real light spots onto the blocks around it (client/DiscoBallRenderer
 * traces its beams through the world every frame). Right-click or a new redstone signal: off, white spots, coloured spots.
 */
public class DiscoBallBlock extends BaseEntityBlock {
    /** 0 off, 1 white spots, 2 coloured spots. */
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 2);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** How the lights move (client/DiscoBallRenderer): sneak + right-click for the next. */
    public static final int ANIMATIONS = 9;
    public static final IntegerProperty ANIMATION = IntegerProperty.create("animation", 0, ANIMATIONS - 1);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3.0, 2.0, 3.0, 13.0, 12.0, 13.0), Block.box(7.5, 12.0, 7.5, 8.5, 16.0, 8.5));

    public DiscoBallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MODE, 1).setValue(POWERED, false).setValue(ANIMATION, 0));
    }


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE, POWERED, ANIMATION);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED; // (drawn by its renderer)
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Nothing for light to bump into (the beams start inside it). */
    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiscoBallBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    /** Each new redstone signal: the next mode (like a right-click). */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            BlockState next = state.setValue(POWERED, powered);
            if (powered) {
                next = next.setValue(MODE, (state.getValue(MODE) + 1) % 3);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.7F + 0.3F * next.getValue(MODE));
            }
            level.setBlock(pos, next, Block.UPDATE_ALL);
        }
    }

    protected InteractionResult useWithoutItem121(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) { // the next light animation
            if (!level.isClientSide) {
                int animation = (state.getValue(ANIMATION) + 1) % ANIMATIONS;
                level.setBlock(pos, state.setValue(ANIMATION, animation), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.6F + 0.1F * animation);
                player.displayClientMessage(Component.translatable("disco.skyblocks.animation",
                        Component.translatable("disco.skyblocks.animation." + animation)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            int mode = (state.getValue(MODE) + 1) % 3;
            level.setBlock(pos, state.setValue(MODE, mode), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.7F + 0.3F * mode);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.disco_ball").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.disco_ball_animation").withStyle(ChatFormatting.GRAY));
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
