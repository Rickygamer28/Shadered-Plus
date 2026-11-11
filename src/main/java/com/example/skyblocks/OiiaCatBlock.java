package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The OIIA cat: a 3D model block like Shadered's Maxwell (drawn by Shadered's model renderer), facing the player
 * who placed it. While it gets a redstone signal it plays the model's own spin-and-bob animation from the start
 * (client/OiiaCatRenderer); without a signal it stands still.
 */
public class OiiaCatBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<OiiaCatBlock> CODEC = simpleCodec(OiiaCatBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 13, 13);

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public OiiaCatBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OiiaCatBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** Fed a fish, the cat hops off its block: your companion now (OiiaCatEntity). */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                                 net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                                                 net.minecraft.world.phys.BlockHitResult hit) {
        if (this != ModBlocks.OIIA_CAT.get() || !stack.is(net.minecraft.tags.ItemTags.CAT_FOOD)) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            OiiaCatEntity cat = ModEntities.OIIA_CAT.get().create(level);
            if (cat != null) {
                level.removeBlock(pos, false);
                float yaw = state.getValue(FACING).toYRot();
                cat.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
                cat.setYHeadRot(yaw);
                cat.setYBodyRot(yaw);
                cat.tame(player);
                level.addFreshEntity(cat);
                level.broadcastEntityEvent(cat, (byte) 7); // (hearts)
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CAT_EAT, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                stack.consume(1, player);
            }
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    /** Placing the cat that completes a 3x3x3 cube of cats turns the cube into the big cat. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BigOiiaCatBlock.tryForm(level, pos);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    /** Redstone on/off switches the animation (synced to clients through the block state). */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
