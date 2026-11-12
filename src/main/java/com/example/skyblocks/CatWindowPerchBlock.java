package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A cat window perch: a little hammock stuck onto a window (or any wall) with suction cups. A cat lies in it, looking out,
 * chattering at the butterflies and birds going past outside (client/CatWindowPerchRenderer). FACING: the way it faces
 * out into the room (the window's behind it).
 */
public class CatWindowPerchBlock extends BaseEntityBlock {
    public static final MapCodec<CatWindowPerchBlock> CODEC = simpleCodec(CatWindowPerchBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // (as made facing south, out along +z from a window at z 0: turned round for the others)
            SHAPES.put(d, Shapes.or(turned(d, 2, 3.5, 1, 14, 6.5, 10), turned(d, 3, 6, 0, 13, 9.5, 1.5)));
        }
    }

    public CatWindowPerchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    /** A box of the south-facing perch (pixels), turned to face d. */
    private static VoxelShape turned(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
        double[] a = turn(d, x0, z0), b = turn(d, x1, z1);
        return Block.box(Math.min(a[0], b[0]), y0, Math.min(a[1], b[1]), Math.max(a[0], b[0]), y1, Math.max(a[1], b[1]));
    }

    /** A point (x, z: pixels) of the south-facing perch, turned to face d (as its model's turned: clockwise from above). */
    static double[] turn(Direction d, double x, double z) {
        return switch (d) {
            case WEST -> new double[] {16 - z, x};
            case NORTH -> new double[] {16 - x, 16 - z};
            case EAST -> new double[] {z, 16 - x};
            default -> new double[] {x, z};
        };
    }

    /** Where a cat lies on it (the middle of the hammock). */
    public static Vec3 perch(BlockPos pos, BlockState state) {
        double[] p = turn(state.getValue(FACING), 8, 5.5);
        return new Vec3(pos.getX() + p[0] / 16, pos.getY() + 4.4 / 16, pos.getZ() + p[1] / 16);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isVertical()) {
            face = context.getHorizontalDirection().getOpposite();
        }
        BlockState state = defaultBlockState().setValue(FACING, face);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos behind = pos.relative(state.getValue(FACING).getOpposite());
        BlockState wall = level.getBlockState(behind);
        return !wall.isAir() && !wall.getCollisionShape(level, behind).isEmpty();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return dir == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, next, level, pos, nextPos);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SimpleRenderedBlockEntity(ModBlockEntities.CAT_WINDOW_PERCH.get(), pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.cat_window_perch").withStyle(ChatFormatting.GRAY));
    }
}
