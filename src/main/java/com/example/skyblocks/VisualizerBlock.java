package com.example.skyblocks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.example.skyblocks.compat.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * A music visualizer: a screen on the wall dancing to the music playing nearby (client/MusicReactor). Screens side
 * by side and stacked join into one big screen. Right-click: the next look (bars, bars from the middle, a scrolling
 * spectrum, a ring of bars, glowing waves); with a dye: its colour; sneak + right-click: rainbow. Drawn by client/VisualizerRenderer.
 */
public class VisualizerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 4);
    private static final String[] MODES = {"bars", "middle", "spectrum", "circle", "waves"};

    public VisualizerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEFT, false).setValue(RIGHT, false)
                .setValue(UP, false).setValue(DOWN, false).setValue(MODE, 0));
    }


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT, UP, DOWN, MODE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> Block.box(0, 0, 0, 16, 16, 2);
            case EAST -> Block.box(0, 0, 0, 2, 16, 16);
            case WEST -> Block.box(14, 0, 0, 16, 16, 16);
            default -> Block.box(0, 0, 14, 16, 16, 16);
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        for (Direction d : new Direction[] {facing.getClockWise(), facing.getCounterClockWise(), Direction.UP, Direction.DOWN}) {
            BlockState o = context.getLevel().getBlockState(context.getClickedPos().relative(d));
            if (joins(o, facing)) {
                state = state.setValue(MODE, o.getValue(MODE)); // (joining a screen: its look)
                break;
            }
        }
        return connected(state, context.getLevel(), context.getClickedPos());
    }

    private boolean joins(BlockState other, Direction facing) {
        return other.is(this) && other.getValue(FACING) == facing;
    }

    private BlockState connected(BlockState state, LevelAccessor level, BlockPos pos) {
        Direction f = state.getValue(FACING);
        return state.setValue(LEFT, joins(level.getBlockState(pos.relative(f.getClockWise())), f))
                .setValue(RIGHT, joins(level.getBlockState(pos.relative(f.getCounterClockWise())), f))
                .setValue(UP, joins(level.getBlockState(pos.above()), f))
                .setValue(DOWN, joins(level.getBlockState(pos.below()), f));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return connected(state, level, pos);
    }

    /** Every screen in this one's joined screen. */
    public static List<BlockPos> screen(BlockGetter level, BlockPos start) {
        BlockState s0 = level.getBlockState(start);
        List<BlockPos> out = new ArrayList<>();
        if (!(s0.getBlock() instanceof VisualizerBlock)) {
            return out;
        }
        Direction f = s0.getValue(FACING);
        ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(start));
        Set<BlockPos> seen = new HashSet<>(List.of(start));
        while (!todo.isEmpty() && out.size() < 256) {
            BlockPos p = todo.poll();
            out.add(p);
            for (Direction d : new Direction[] {f.getClockWise(), f.getCounterClockWise(), Direction.UP, Direction.DOWN}) {
                BlockPos q = p.relative(d);
                BlockState s = level.getBlockState(q);
                if (s.getBlock() instanceof VisualizerBlock && s.getValue(FACING) == f && seen.add(q)) {
                    todo.add(q);
                }
            }
        }
        return out;
    }

    /** A new screen joined to others takes their colour. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof GlowColourBlockEntity be)) {
            return;
        }
        for (BlockPos p : screen(level, pos)) {
            if (!p.equals(pos) && level.getBlockEntity(p) instanceof GlowColourBlockEntity other) {
                if (other.rgb()) {
                    be.setRgb(true);
                } else {
                    be.setColour(other.colour());
                }
                return;
            }
        }
    }

    protected ItemInteractionResult useItemOn121(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            for (BlockPos p : screen(level, pos)) {
                if (level.getBlockEntity(p) instanceof GlowColourBlockEntity be) {
                    be.setColour(dye.getDyeColor().getTextureDiffuseColor());
                }
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    protected InteractionResult useWithoutItem121(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            List<BlockPos> all = screen(level, pos);
            if (player.isShiftKeyDown()) {
                for (BlockPos p : all) {
                    if (level.getBlockEntity(p) instanceof GlowColourBlockEntity be) {
                        be.setRgb(true);
                    }
                }
                player.displayClientMessage(Component.translatable("message.skyblocks.rgb_on").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            } else {
                int mode = (state.getValue(MODE) + 1) % MODES.length;
                for (BlockPos p : all) {
                    level.setBlock(p, level.getBlockState(p).setValue(MODE, mode), Block.UPDATE_CLIENTS);
                }
                player.displayClientMessage(Component.translatable("message.skyblocks.visualizer_" + MODES[mode]).withStyle(ChatFormatting.AQUA), true);
            }
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.4F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return GlowColourBlockEntity.visualizer(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.visualizer").withStyle(ChatFormatting.GRAY));
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
