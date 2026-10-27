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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A neon sign: your own glowing text on a clear panel against a wall. Right-click to write it and choose its colour,
 * how it glows (steady, flickering, pulsing, rainbow) and its size. Drawn by client/NeonSignRenderer.
 */
public class NeonSignBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Another neon sign facing the same way on that side (as you face it): the panels join into one. */
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    public NeonSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEFT, false).setValue(RIGHT, false).setValue(UP, false).setValue(DOWN, false));
    }


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT, UP, DOWN);
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
        return connected(defaultBlockState().setValue(FACING, facing), context.getLevel(), context.getClickedPos());
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

    /** Every sign in this one's joined panel (side by side and stacked, facing the same way). */
    public static java.util.List<BlockPos> panel(net.minecraft.world.level.BlockGetter level, BlockPos start) {
        BlockState s0 = level.getBlockState(start);
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        if (!(s0.getBlock() instanceof NeonSignBlock)) {
            return out;
        }
        Direction f = s0.getValue(FACING);
        java.util.ArrayDeque<BlockPos> todo = new java.util.ArrayDeque<>(java.util.List.of(start));
        java.util.Set<BlockPos> seen = new java.util.HashSet<>(java.util.List.of(start));
        while (!todo.isEmpty() && out.size() < 256) {
            BlockPos p = todo.poll();
            out.add(p);
            for (Direction d : new Direction[] {f.getClockWise(), f.getCounterClockWise(), Direction.UP, Direction.DOWN}) {
                BlockPos q = p.relative(d);
                BlockState s = level.getBlockState(q);
                if (s.getBlock() instanceof NeonSignBlock && s.getValue(FACING) == f && seen.add(q)) {
                    todo.add(q);
                }
            }
        }
        return out;
    }

    /** A new sign joined to a panel takes its colour. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof NeonSignBlockEntity be)) {
            return;
        }
        for (BlockPos p : panel(level, pos)) {
            if (!p.equals(pos) && level.getBlockEntity(p) instanceof NeonSignBlockEntity other) {
                be.setBackground(other.background());
                return;
            }
        }
    }

    protected InteractionResult useWithoutItem121(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            com.example.skyblocks.client.ClientAccess.openNeonSign(pos); // (client-only class, never loaded on a server)
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NeonSignBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.neon_sign").withStyle(ChatFormatting.GRAY));
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
