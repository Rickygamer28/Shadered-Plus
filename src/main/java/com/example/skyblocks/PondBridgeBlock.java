package com.example.skyblocks;

import java.util.List;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A little arched wooden bridge, for over a koi pond: put a row of them across (on top of the pond blocks); the row
 * arches up from both ends to its middle (RISE: how high this piece of it is), with red railings along both sides.
 * The pond carries on underneath it: the koi swim under the bridge.
 */
public class PondBridgeBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty RISE = IntegerProperty.create("rise", 0, 2);
    private static final VoxelShape[][] SHAPES = new VoxelShape[2][3];

    static {
        for (int r = 0; r < 3; r++) {
            double d = r * 2.0;
            // (along z: the deck and both railings; along x: the same turned)
            SHAPES[1][r] = Shapes.or(Block.box(2.0, d, 0.0, 14.0, d + 2.0, 16.0), Block.box(2.0, d, 0.0, 3.0, d + 9.0, 16.0),
                    Block.box(13.0, d, 0.0, 14.0, d + 9.0, 16.0));
            SHAPES[0][r] = Shapes.or(Block.box(0.0, d, 2.0, 16.0, d + 2.0, 14.0), Block.box(0.0, d, 2.0, 16.0, d + 9.0, 3.0),
                    Block.box(0.0, d, 13.0, 16.0, d + 9.0, 14.0));
        }
    }

    public PondBridgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(RISE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, RISE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(AXIS) == Direction.Axis.X ? 0 : 1][state.getValue(RISE)];
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // (along the way the player is looking, unless it carries on a bridge beside it)
        Direction.Axis axis = context.getHorizontalDirection().getAxis();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState next = context.getLevel().getBlockState(context.getClickedPos().relative(d));
            if (next.getBlock() == this && next.getValue(AXIS) == d.getAxis()) {
                axis = d.getAxis();
                break;
            }
        }
        BlockState state = defaultBlockState().setValue(AXIS, axis);
        return state.setValue(RISE, rise(context.getLevel(), context.getClickedPos(), axis));
    }

    /** How high this piece of a row arches: 0 at its ends, up to 2 in the middle of a long one. */
    private int rise(BlockGetter level, BlockPos pos, Direction.Axis axis) {
        Direction back = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        int a = run(level, pos, back, axis), b = run(level, pos, back.getOpposite(), axis);
        return Math.min(Math.min(a, b), 2);
    }

    private int run(BlockGetter level, BlockPos pos, Direction d, Direction.Axis axis) {
        int n = 0;
        BlockPos.MutableBlockPos p = pos.mutable();
        while (n < 16) {
            p.move(d);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() != this || s.getValue(AXIS) != axis) break;
            n++;
        }
        return n;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        Direction.Axis axis = state.getValue(AXIS);
        return dir.getAxis() == axis ? state.setValue(RISE, rise(level, pos, axis)) : state;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.pond_bridge").withStyle(ChatFormatting.GRAY));
    }
}
