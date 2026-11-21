package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A floor sensor: a small block near an elevator's landing (on a floor, a wall or a ceiling) giving a redstone signal
 * for that floor - to things next to it, and through the block it's on. Right-click: what it gives a signal for -
 * while the cabin's stopped here, a pulse as it arrives, a pulse as it leaves, or while the elevator's moving. Looks for
 * the nearest cabin within 4 blocks across and its floor within a block or two of its own height, every tick.
 */
public class ElevatorSensorBlock extends Block {
    public static final MapCodec<ElevatorSensorBlock> CODEC = simpleCodec(ElevatorSensorBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 3);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** (The cabin stopped here last look; ticks of a pulse left.) */
    public static final BooleanProperty HERE = BooleanProperty.create("here");
    public static final IntegerProperty PULSE = IntegerProperty.create("pulse", 0, 10);
    private static final String[] MODES = {"here", "arrive", "leave", "moving"};
    private static final java.util.Map<Direction, VoxelShape> SHAPES = java.util.Map.of(
            Direction.UP, Block.box(4, 0, 4, 12, 3, 12), Direction.DOWN, Block.box(4, 13, 4, 12, 16, 12),
            Direction.NORTH, Block.box(4, 4, 13, 12, 12, 16), Direction.SOUTH, Block.box(4, 4, 0, 12, 12, 3),
            Direction.WEST, Block.box(13, 4, 4, 16, 12, 12), Direction.EAST, Block.box(0, 4, 4, 3, 12, 12));

    public ElevatorSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(MODE, 0).setValue(POWERED, false).setValue(HERE, false).setValue(PULSE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE, POWERED, HERE, PULSE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!old.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int mode = (state.getValue(MODE) + 1) % MODES.length;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(MODE, mode).setValue(PULSE, 0), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.6F, 0.6F + mode * 0.15F);
        } else {
            player.displayClientMessage(Component.translatable("message.skyblocks.elevator_sensor." + MODES[mode]), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, 1);
        // the nearest cabin: stopped at a floor by this height? moving?
        List<ElevatorCabinEntity> cabins = level.getEntitiesOfClass(ElevatorCabinEntity.class, new AABB(pos).inflate(8.0, 48.0, 8.0));
        ElevatorCabinEntity cabin = null;
        double best = Double.MAX_VALUE;
        for (ElevatorCabinEntity c : cabins) {
            // (within 4 blocks of its edge)
            if (Math.max(Math.abs(c.getX() - pos.getX() - 0.5), Math.abs(c.getZ() - pos.getZ() - 0.5)) > c.half() + 4.0) {
                continue;
            }
            double d = (c.getX() - pos.getX() - 0.5) * (c.getX() - pos.getX() - 0.5) + (c.getZ() - pos.getZ() - 0.5) * (c.getZ() - pos.getZ() - 0.5);
            if (d < best) {
                best = d;
                cabin = c;
            }
        }
        int off = cabin == null ? 99 : pos.getY() - Math.round(cabin.landing());
        boolean here = cabin != null && !cabin.moving() && off >= -1 && off <= 2; // (the moment it stops)
        boolean moving = cabin != null && cabin.moving();
        boolean wasHere = state.getValue(HERE);
        int pulse = Math.max(0, state.getValue(PULSE) - 1);
        int mode = state.getValue(MODE);
        if (mode == 1 && here && !wasHere || mode == 2 && !here && wasHere) {
            pulse = 10; // (10 ticks)
        }
        boolean on = switch (mode) {
            case 0 -> here;
            case 3 -> moving;
            default -> pulse > 0;
        };
        BlockState next = state.setValue(HERE, here).setValue(PULSE, pulse).setValue(POWERED, on);
        if (next != state) {
            level.setBlock(pos, next, on != state.getValue(POWERED) ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
            if (on != state.getValue(POWERED)) {
                // (the block it's on passes the signal on too)
                level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), this);
            }
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    /** Through the block it's on. */
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && direction == state.getValue(FACING) ? 15 : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (!next.is(this) && state.getValue(POWERED)) {
            level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), this);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        Direction f = state.getValue(FACING);
        return level.getBlockState(pos.relative(f.getOpposite())).isFaceSturdy(level, pos.relative(f.getOpposite()), f);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return dir == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, next, level, pos, nextPos);
    }
}
