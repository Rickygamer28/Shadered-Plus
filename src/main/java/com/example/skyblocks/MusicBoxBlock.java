package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A music box: click it and its lid opens, a little dancer rises and turns, its key turns, and it plays a tune on
 * bells (MusicBoxSongs: Brahms' lullaby, Twinkle Twinkle, Fur Elise - sneak-click for the next); click again to stop.
 * While it plays, the fish in aquariums near it and the fireflies over ponds near it move in time with the notes, and
 * the synthwave skies react to it (client/MusicReactor hears note-block sounds). The tune is played on each computer
 * from when it started (MusicBoxBlockEntity), so everyone hears it together.
 */
public class MusicBoxBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty PLAYING = BooleanProperty.create("playing");
    public static final IntegerProperty SONG = IntegerProperty.create("song", 0, MusicBoxSongs.COUNT - 1);
    private static final VoxelShape SHAPE_NS = Block.box(3, 0, 4, 13, 7, 12), SHAPE_EW = Block.box(4, 0, 3, 12, 7, 13);

    public MusicBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PLAYING, false).setValue(SONG, 0));
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PLAYING, SONG);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); // (facing you)
    }

    protected InteractionResult useWithoutItem121(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState next;
            if (player.isShiftKeyDown()) { // (the next tune, from its start)
                next = state.setValue(SONG, (state.getValue(SONG) + 1) % MusicBoxSongs.COUNT).setValue(PLAYING, true);
            } else {
                next = state.setValue(PLAYING, !state.getValue(PLAYING));
            }
            if (next.getValue(PLAYING) && level.getBlockEntity(pos) instanceof MusicBoxBlockEntity box) {
                box.start(level.getGameTime() + 4L);
            }
            level.setBlock(pos, next, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.25F, next.getValue(PLAYING) ? 1.8F : 1.5F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MusicBoxBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, ModBlockEntities.MUSIC_BOX.get(), MusicBoxBlockEntity::clientTick) : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.music_box").withStyle(ChatFormatting.GRAY));
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
