package com.example.skyblocks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A dance floor tile. Touching tiles make one floor that plays one animation across all of it (drawn by
 * client/DanceFloorRenderer). A redstone signal on any one tile of a floor makes the whole floor go through all the
 * animations, one after another, fading smoothly from one to the next; without a signal it stays on the one it shows.
 * Right-click with an empty hand: the next animation.
 */
public class DanceFloorBlock extends Block {
    public static final int ANIMATIONS = 9;
    /** Ticks each animation plays while the floor has a signal. */
    public static final int PERIOD = 200;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty ANIMATION = IntegerProperty.create("animation", 0, ANIMATIONS - 1);
    private static final int MAX_FLOOR = 4096;

    public DanceFloorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false).setValue(ANIMATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, ANIMATION);
    }

    /** The animation a floor shows: its own, or while it has a signal, the one the cycle has got to. */
    public static int shown(int animation, boolean powered, long gameTime) {
        return powered ? (int) ((animation + gameTime / PERIOD) % ANIMATIONS) : animation;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        int animation = 0;
        for (Direction d : Direction.values()) {
            BlockState next = level.getBlockState(pos.relative(d));
            if (next.getBlock() instanceof DanceFloorBlock) {
                animation = next.getValue(ANIMATION); // (joins the floor it touches)
                break;
            }
        }
        return defaultBlockState().setValue(ANIMATION, animation).setValue(POWERED, level.hasNeighborSignal(pos));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !oldState.is(this)) {
            setAnimation(level, floor(level, pos), state.getValue(ANIMATION)); // (two floors joined: one animation)
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) {
            return;
        }
        List<BlockPos> floor = floor(level, pos);
        boolean wasCycling = anyPowered(level, floor);
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        if (wasCycling && !anyPowered(level, floor)) {
            // the last signal gone: stay on the animation it was showing
            setAnimation(level, floor, shown(state.getValue(ANIMATION), true, level.getGameTime()));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            List<BlockPos> floor = floor(level, pos);
            boolean powered = anyPowered(level, floor);
            int next = (shown(state.getValue(ANIMATION), powered, level.getGameTime()) + 1) % ANIMATIONS;
            // (while cycling, stored so that the cycle shows `next` now)
            int stored = powered ? Math.floorMod(next - (int) ((level.getGameTime() / PERIOD) % ANIMATIONS), ANIMATIONS) : next;
            setAnimation(level, floor, stored);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6F, 0.8F + 0.1F * next);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean anyPowered(Level level, List<BlockPos> floor) {
        for (BlockPos p : floor) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DanceFloorBlock && s.getValue(POWERED)) {
                return true;
            }
        }
        return false;
    }

    private static void setAnimation(Level level, List<BlockPos> floor, int animation) {
        for (BlockPos p : floor) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof DanceFloorBlock && s.getValue(ANIMATION) != animation) {
                level.setBlock(p, s.setValue(ANIMATION, animation), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** All the tiles touching this one (through faces), this one included. */
    public static List<BlockPos> floor(BlockGetter level, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        seen.add(start.immutable());
        while (!queue.isEmpty() && out.size() < MAX_FLOOR) {
            BlockPos p = queue.poll();
            out.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n) && level.getBlockState(n).getBlock() instanceof DanceFloorBlock) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        return out;
    }
}
