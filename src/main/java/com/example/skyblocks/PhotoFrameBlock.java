package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A framed photo (placed from a PhotoItem): hung on a wall, or a little frame standing on a table. FACING is the way the
 * picture faces. Breaking it gives the photo back. The picture itself is client/PhotoFrameRenderer.
 */
public class PhotoFrameBlock extends BaseEntityBlock {
    public static final MapCodec<PhotoFrameBlock> CODEC = simpleCodec(PhotoFrameBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty STANDING = BooleanProperty.create("standing");

    private static final Map<Direction, VoxelShape> WALL = Map.of(
            Direction.NORTH, Block.box(1, 2.5, 14.5, 15, 13.5, 16), Direction.SOUTH, Block.box(1, 2.5, 0, 15, 13.5, 1.5),
            Direction.WEST, Block.box(14.5, 2.5, 1, 16, 13.5, 15), Direction.EAST, Block.box(0, 2.5, 1, 1.5, 13.5, 15));
    private static final Map<Direction, VoxelShape> STAND = Map.of(
            Direction.NORTH, Block.box(3, 0, 6.5, 13, 9, 11), Direction.SOUTH, Block.box(3, 0, 5, 13, 9, 9.5),
            Direction.WEST, Block.box(6.5, 0, 3, 11, 9, 13), Direction.EAST, Block.box(5, 0, 3, 9.5, 9, 13));

    public PhotoFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STANDING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STANDING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(STANDING) ? STAND : WALL).get(state.getValue(FACING));
    }

    /** On a wall: something solid behind it. Standing: something to stand on. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(STANDING)) {
            return Block.canSupportCenter(level, pos.below(), Direction.UP);
        }
        BlockPos behind = pos.relative(state.getValue(FACING).getOpposite());
        return level.getBlockState(behind).isSolid();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** The photo back, however it went (broken, or what held it up gone). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof PhotoFrameBlockEntity be && be.photo() != null) {
            Block.popResource(level, pos, be.stack());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return level.getBlockEntity(pos) instanceof PhotoFrameBlockEntity be && be.photo() != null ? be.stack() : new ItemStack(ModItems.PHOTO.get());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotoFrameBlockEntity(pos, state);
    }
}
