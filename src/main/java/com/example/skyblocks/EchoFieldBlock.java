package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * An echo field: round it, whatever moves - players, mobs, dropped items - leaves a trail of fading rainbow ghosts of
 * itself, like a long-exposure photo (client/EchoField). Right-click: how far it reaches (4, 8 or 12 blocks).
 */
public class EchoFieldBlock extends BaseEntityBlock {
    public static final MapCodec<EchoFieldBlock> CODEC = simpleCodec(EchoFieldBlock::new);
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 0, 2);
    public static final int[] RADII = {4, 8, 12};
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 6, 14);

    public EchoFieldBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SIZE, 1));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIZE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int size = (state.getValue(SIZE) + 1) % 3;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(SIZE, size), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F + size * 0.3F);
        } else {
            player.displayClientMessage(Component.translatable("message.skyblocks.echo_field", RADII[size]), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SimpleRenderedBlockEntity(ModBlockEntities.ECHO_FIELD.get(), pos, state);
    }
}
