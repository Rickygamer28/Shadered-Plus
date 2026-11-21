package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A phase block: a solid sci-fi wall panel. Given a redstone signal it burns away - dissolving with glowing edges - and
 * can be walked through; the signal gone, it forms again (client/PhaseBlockRenderer draws it all). Phase blocks
 * touching each other are one wall: a signal to any of them opens them all. Right-click: the block it looks like (any)
 * and how it opens - burning, pixels, a scan, a hologram, a ripple (client/PhaseScreen) - for its whole wall.
 */
public class PhaseBlock extends BaseEntityBlock {
    public static final MapCodec<PhaseBlock> CODEC = simpleCodec(PhaseBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.POWERED;
    /** (The most blocks one wall opens at once.) */
    private static final int MOST = 512;

    public PhaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!old.is(this)) {
            update(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        update(level, pos);
    }

    /** The whole wall (the phase blocks touching this one, and theirs): open if any of it has a signal. */
    private void update(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        List<BlockPos> wall = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> next = new ArrayDeque<>();
        next.add(pos);
        seen.add(pos);
        boolean signal = false;
        while (!next.isEmpty() && wall.size() < MOST) {
            BlockPos p = next.poll();
            wall.add(p);
            signal |= level.hasNeighborSignal(p);
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (seen.add(q) && level.getBlockState(q).is(this)) {
                    next.add(q);
                }
            }
        }
        boolean changed = false;
        for (BlockPos p : wall) {
            BlockState st = level.getBlockState(p);
            if (st.is(this) && st.getValue(OPEN) != signal) {
                level.setBlock(p, st.setValue(OPEN, signal), Block.UPDATE_CLIENTS);
                changed = true;
            }
        }
        if (changed) {
            level.playSound(null, pos, signal ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, signal ? 1.6F : 1.4F);
        }
    }

    /** Right-click: its look and how it opens (client/PhaseScreen). */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide && level.getBlockEntity(pos) instanceof PhaseBlockEntity be) {
            com.example.skyblocks.client.PhaseScreen.open(pos, be);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return state.getValue(OPEN);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhaseBlockEntity(pos, state);
    }
}
