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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An infinity cube: six half mirrors with LED strips along the inside edges, so the edges are seen repeated in every
 * direction, a glowing grid going on forever (client/InfinityCubeRenderer works it out per pixel). Infinity cubes that
 * fill a whole box together make one big cube. Right-click to change the colour (sneak to go back).
 */
public class InfinityCubeBlock extends Block {
    /** Same colours as the Infinity Mirror. */
    public static final IntegerProperty COLOR = InfinityMirrorBlock.COLOR;
    private static final int MAX_JOINED = 512;

    public InfinityCubeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COLOR, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // next to an infinity cube: take its colour (so a big cube stays one colour)
        for (Direction d : Direction.values()) {
            BlockState next = context.getLevel().getBlockState(context.getClickedPos().relative(d));
            if (next.getBlock() instanceof InfinityCubeBlock) {
                return defaultBlockState().setValue(COLOR, next.getValue(COLOR));
            }
        }
        return defaultBlockState();
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            int step = player.isShiftKeyDown() ? InfinityMirrorBlock.COLORS - 1 : 1;
            int color = (state.getValue(COLOR) + step) % InfinityMirrorBlock.COLORS;
            for (BlockPos p : joined(level, pos)) {
                level.setBlock(p, level.getBlockState(p).setValue(COLOR, color), Block.UPDATE_ALL);
            }
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.4F + color * 0.05F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The infinity cubes touching this one (and those touching them...). */
    private static Set<BlockPos> joined(Level level, BlockPos start) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        found.add(start);
        queue.add(start);
        while (!queue.isEmpty() && found.size() < MAX_JOINED) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!found.contains(n) && level.getBlockState(n).getBlock() instanceof InfinityCubeBlock) {
                    found.add(n);
                    queue.add(n);
                }
            }
        }
        return found;
    }
}
