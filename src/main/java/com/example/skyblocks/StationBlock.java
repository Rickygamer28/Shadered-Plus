package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A train station: a post beside a train rail (up to two blocks off), a hologram board over it with its name and
 * whether a train's coming (client/StationRenderer). Right-click: its name, and a train called to it (client/StationScreen).
 */
public class StationBlock extends BaseEntityBlock {
    public static final MapCodec<StationBlock> CODEC = simpleCodec(StationBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);

    public StationBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide && level.getBlockEntity(pos) instanceof StationBlockEntity be) {
            com.example.skyblocks.client.StationScreen.open(pos, be);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Calls the nearest train on its line to it; says how it went. */
    public static void call(ServerLevel level, BlockPos station, Player player) {
        BlockPos rail = TrainLine.railBy(level, station);
        if (rail == null) {
            player.displayClientMessage(Component.translatable("message.skyblocks.station.no_rail"), true);
            return;
        }
        List<TrainEntity> trains = level.getEntitiesOfClass(TrainEntity.class, new AABB(station).inflate(512.0, 128.0, 512.0));
        trains.sort((a, b) -> Double.compare(a.distanceToSqr(station.getCenter()), b.distanceToSqr(station.getCenter())));
        for (TrainEntity t : trains) {
            if (t.goTo(rail)) {
                player.displayClientMessage(Component.translatable("message.skyblocks.station.called"), true);
                return;
            }
        }
        player.displayClientMessage(Component.translatable("message.skyblocks.station.no_train"), true);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationBlockEntity(pos, state);
    }
}
