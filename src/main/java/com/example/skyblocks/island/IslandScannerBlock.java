package com.example.skyblocks.island;

import com.example.skyblocks.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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
    /** Matches the model: base, core and lens. */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 6, 16), Block.box(3, 6, 3, 13, 14, 13),
            Block.box(5, 14, 5, 11, 16, 11));

    public IslandScannerBlock(Properties properties) {
        super(properties);
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            ClientAccess.openIslandScanner(pos); // client-only class, never loaded on a server
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
