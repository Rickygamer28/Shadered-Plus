package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A television: a thin panel that plays a video - a YouTube link, any link to a video, or a video file dragged onto its
 * screen's window (client/tv). Put them together in a wall, facing the same way (up to 16 by 12), and they're one big
 * television: what it plays and how is kept by the one at its bottom left (as you look at it).
 */
public class TelevisionBlock extends BaseEntityBlock {
    public static final MapCodec<TelevisionBlock> CODEC = simpleCodec(TelevisionBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final int MAX_W = 16, MAX_H = 12;
    /** Against the wall behind it (the model faces north: its back at the south side). */
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 14, 16, 16, 16), // north
            Block.box(0, 0, 0, 16, 16, 2), // south
            Block.box(14, 0, 0, 16, 16, 16), // west
            Block.box(0, 0, 0, 2, 16, 16)}; // east

    public TelevisionBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPES[1];
            case WEST -> SHAPES[2];
            case EAST -> SHAPES[3];
            default -> SHAPES[0];
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // (facing you - or, put against the side of another, the way it faces: a wall goes on growing)
        Direction face = context.getClickedFace();
        BlockState against = context.getLevel().getBlockState(context.getClickedPos().relative(face.getOpposite()));
        if (against.is(this) && (face.getAxis().isVertical() || face.getAxis() != against.getValue(FACING).getAxis())) {
            return defaultBlockState().setValue(FACING, against.getValue(FACING));
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    private static boolean same(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState st = level.getBlockState(pos);
        return st.getBlock() instanceof TelevisionBlock && st.getValue(FACING) == facing;
    }

    /** The way to its right, as you look at it. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** The first column's bottom of the wall it's in (TvWall: walls may turn corners). */
    public static BlockPos anchor(BlockGetter level, BlockPos pos) {
        return TvWall.anchor(level, pos);
    }

    /** Whether it's the first of its wall (the one that keeps what it plays, and draws the picture). */
    public static boolean isAnchor(BlockGetter level, BlockPos pos, Direction facing) {
        return TvWall.anchor(level, pos).equals(pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            com.example.skyblocks.client.ClientAccess.openTelevision(anchor(level, pos)); // (client-only class, never loaded on a server)
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // (the wall's bottom left gone: what it played goes on at the new bottom left)
        if (!level.isClientSide && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TelevisionBlockEntity tv && !tv.source().isEmpty()) {
            Direction facing = state.getValue(FACING);
            super.onRemove(state, level, pos, newState, moved);
            TvWall.Column after = TvWall.next(level, pos, facing);
            for (BlockPos next : new BlockPos[] {after == null ? pos.above() : after.bottom(), pos.above()}) {
                if (level.getBlockState(next).getBlock() instanceof TelevisionBlock
                        && level.getBlockEntity(anchor(level, next)) instanceof TelevisionBlockEntity other && other.source().isEmpty()) {
                    other.copyFrom(tv);
                    break;
                }
            }
            return;
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TelevisionBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.television").withStyle(ChatFormatting.GRAY));
    }
}
