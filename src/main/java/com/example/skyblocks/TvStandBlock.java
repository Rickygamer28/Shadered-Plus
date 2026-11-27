package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Something to hold a television: a stand (a foot and a neck, for one under a television standing free) or a wall
 * mount (an arm out from the wall, for one held off it). Placed facing you, as the television on it will.
 */
public class TvStandBlock extends HorizontalDirectionalBlock {
    /** Its shapes, by facing (north, south, west, east). */
    public static final VoxelShape[] STAND_SHAPES = shapes(Shapes.or(Block.box(2, 0, 6, 14, 1, 15), Block.box(7, 1, 12, 9, 16, 15)));
    public static final VoxelShape[] MOUNT_SHAPES = shapes(Shapes.or(Block.box(4, 4, 15, 12, 12, 16), Block.box(7, 7, 1, 9, 9, 15),
            Block.box(5, 5, 0, 11, 11, 1)));
    private final VoxelShape[] shapes;

    public TvStandBlock(Properties properties, VoxelShape[] shapes) {
        super(properties);
        this.shapes = shapes;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new TvStandBlock(p, shapes));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> shapes[1];
            case WEST -> shapes[2];
            case EAST -> shapes[3];
            default -> shapes[0];
        };
    }

    /** A north-facing shape turned to each way (north, south, west, east). */
    private static VoxelShape[] shapes(VoxelShape north) {
        VoxelShape[] out = new VoxelShape[4];
        Direction[] ds = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (int i = 0; i < 4; i++) {
            VoxelShape[] acc = {Shapes.empty()};
            Direction d = ds[i];
            north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
                double[] a = turn(x0, z0, d), b = turn(x1, z1, d);
                acc[0] = Shapes.or(acc[0], Shapes.box(Math.min(a[0], b[0]), y0, Math.min(a[1], b[1]), Math.max(a[0], b[0]), y1, Math.max(a[1], b[1])));
            });
            out[i] = acc[0];
        }
        return out;
    }

    /** (x, z) of the north-facing shape, turned as the model is for this facing (its y rotation). */
    private static double[] turn(double x, double z, Direction d) {
        return switch (d) {
            case SOUTH -> new double[] {1 - x, 1 - z};
            case WEST -> new double[] {z, 1 - x};
            case EAST -> new double[] {1 - z, x};
            default -> new double[] {x, z};
        };
    }
}
