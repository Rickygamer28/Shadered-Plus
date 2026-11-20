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
 * A fractal cube: a glass case with a glowing 3D fractal in it, slowly turning and changing shape, pulsing to music
 * (client/FractalCubeRenderer, shaders/core/fractal_cube). Right-click: the next fractal (bulb, sponge, pyramid, maze).
 */
public class FractalCubeBlock extends BaseEntityBlock {
    public static final MapCodec<FractalCubeBlock> CODEC = simpleCodec(FractalCubeBlock::new);
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 3);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 15, 15);
    private static final String[] NAMES = {"bulb", "sponge", "pyramid", "crystal"};

    public FractalCubeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int kind = (state.getValue(KIND) + 1) % 4;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(KIND, kind), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.9F, 0.8F + kind * 0.15F);
        } else {
            player.displayClientMessage(Component.translatable("message.skyblocks.fractal_cube." + NAMES[kind]), true);
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
        return new SimpleRenderedBlockEntity(ModBlockEntities.FRACTAL_CUBE.get(), pos, state);
    }
}
