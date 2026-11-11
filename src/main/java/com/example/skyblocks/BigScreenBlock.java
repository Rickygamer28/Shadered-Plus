package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A big screen for the arcade: a thin panel. Put them together in a wall, facing the same way - any size, up to 16 by
 * 12 - and they're one screen, showing whoever's playing at the nearest arcade cabinet (client/BigScreenRenderer).
 */
public class BigScreenBlock extends BaseEntityBlock {
    public static final MapCodec<BigScreenBlock> CODEC = simpleCodec(BigScreenBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Against the wall behind it (the model's facing north: its back at the south side). */
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 13, 16, 16, 16), // north
            Block.box(0, 0, 0, 16, 16, 3), // south
            Block.box(13, 0, 0, 16, 16, 16), // west
            Block.box(0, 0, 0, 3, 16, 16)}; // east

    public BigScreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPES[1];
            case WEST -> SHAPES[2];
            case EAST -> SHAPES[3];
            default -> SHAPES[0];
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // (facing you - or, put against the side of another screen, the way it faces: a wall goes on growing)
        Direction face = context.getClickedFace();
        BlockState against = context.getLevel().getBlockState(context.getClickedPos().relative(face.getOpposite()));
        if (against.is(this) && face.getAxis().isVertical() || against.is(this) && face.getAxis() != against.getValue(FACING).getAxis()) {
            return defaultBlockState().setValue(FACING, against.getValue(FACING));
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SimpleRenderedBlockEntity(ModBlockEntities.BIG_SCREEN.get(), pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.big_screen").withStyle(ChatFormatting.GRAY));
    }
}
