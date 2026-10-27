package com.example.skyblocks;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An infinity mirror: its front shows a tunnel of LED rings going on forever (drawn by client/InfinityMirrorRenderer,
 * the tunnel itself is worked out per pixel in a shader, so it costs about nothing). Placed facing you; infinity
 * mirrors side by side on the same wall make one big frame. Right-click to change the colour of the whole frame
 * (sneak to go back).
 */
public class InfinityMirrorBlock extends Block {
    /** The side the tunnel is seen from. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** 0 blue, 1 red, 2 green, 3 cyan, 4 pink, 5 white, 6 gold, 7 rainbow. */
    public static final int COLORS = 8;
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, COLORS - 1);
    /** Most blocks recoloured at once (one frame). */
    private static final int MAX_FRAME = 256;

    public InfinityMirrorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLOR, 0));
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        // next to an infinity mirror facing the same way: take its colour (so a frame stays one colour)
        Level level = context.getLevel();
        for (Direction d : Direction.values()) {
            if (d.getAxis() == facing.getAxis()) {
                continue;
            }
            BlockState next = level.getBlockState(context.getClickedPos().relative(d));
            if (next.getBlock() instanceof InfinityMirrorBlock && next.getValue(FACING) == facing) {
                return defaultBlockState().setValue(FACING, facing).setValue(COLOR, next.getValue(COLOR));
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            int step = player.isShiftKeyDown() ? COLORS - 1 : 1;
            int color = (state.getValue(COLOR) + step) % COLORS;
            for (BlockPos p : frame(level, pos, state.getValue(FACING))) {
                BlockState s = level.getBlockState(p);
                level.setBlock(p, s.setValue(COLOR, color), Block.UPDATE_ALL);
            }
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.4F + color * 0.05F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The infinity mirrors joined to this one: same facing, side by side on the same wall. */
    private static Set<BlockPos> frame(Level level, BlockPos start, Direction facing) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        found.add(start);
        queue.add(start);
        while (!queue.isEmpty() && found.size() < MAX_FRAME) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                if (d.getAxis() == facing.getAxis()) {
                    continue;
                }
                BlockPos n = p.relative(d);
                BlockState s = level.getBlockState(n);
                if (!found.contains(n) && s.getBlock() instanceof InfinityMirrorBlock && s.getValue(FACING) == facing) {
                    found.add(n);
                    queue.add(n);
                }
            }
        }
        return found;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
