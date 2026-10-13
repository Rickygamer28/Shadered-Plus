package com.example.skyblocks.island;

import com.example.skyblocks.client.ClientAccess;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Island Scanner: right-click opens a small menu to choose the area around it (up to 25x25) and scan it into a
 * Sky Island item (client/island/IslandScannerScreen, then ScanIslandPayload).
 */
public class IslandScannerBlock extends BaseEntityBlock {
    public static final MapCodec<IslandScannerBlock> CODEC = simpleCodec(IslandScannerBlock::new);

    public IslandScannerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IslandScannerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            ClientAccess.openIslandScanner(pos); // client-only class, never loaded on a server
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
