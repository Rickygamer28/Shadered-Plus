package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** The head hung on a wall, on a wooden plaque, looking out from the wall (FACING). Placed by the head item. */
public class ZinogreWallHeadBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // facing north (looking out to -Z, wall at the south side): plaque against the wall, head sticking out
        double[][] boxes = {{1, 1, 14, 15, 15, 16}, {3, 2, 1, 13, 14, 14}};
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                shape = Shapes.or(shape, rotated(b, facing));
            }
            SHAPES.put(facing, shape);
        }
    }

    /** A box given for a north-facing head, turned to face the given way. */
    private static VoxelShape rotated(double[] b, Direction facing) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]);
            case WEST -> Block.box(b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]);
            case EAST -> Block.box(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]);
            default -> Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
        };
    }

    public final boolean stygian;

    public ZinogreWallHeadBlock(boolean stygian, Properties properties) {
        super(properties);
        this.stygian = stygian;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal()) {
                return defaultBlockState().setValue(FACING, direction.getOpposite());
            }
        }
        return null;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZinogreHeadBlockEntity(pos, state);
    }
}
