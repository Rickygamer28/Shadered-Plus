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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A koi pond: clear water with koi swimming lazily under lily pads, light rippling on the pebbles. Ponds side by side
 * join into one bigger pond (no stone rim between them). Drawn by client/KoiPondRenderer (shaders/core/koi_pond).
 */
public class KoiPondBlock extends BaseEntityBlock {
    public static final MapCodec<KoiPondBlock> CODEC = simpleCodec(KoiPondBlock::new);
    /** Another pond on that side: the water carries on there. */
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");

    public KoiPondBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false).setValue(WEST, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = Block.box(0, 0, 0, 16, 1, 16);
        if (!state.getValue(NORTH)) s = Shapes.or(s, Block.box(0, 0, 0, 16, 16, 2));
        if (!state.getValue(SOUTH)) s = Shapes.or(s, Block.box(0, 0, 14, 16, 16, 16));
        if (!state.getValue(WEST)) s = Shapes.or(s, Block.box(0, 0, 0, 2, 16, 16));
        if (!state.getValue(EAST)) s = Shapes.or(s, Block.box(14, 0, 0, 16, 16, 16));
        return s;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(0, 0, 0, 16, 14.5, 16); // (you can walk on the water)
    }

    private BlockState connected(BlockState state, LevelAccessor level, BlockPos pos) {
        return state.setValue(NORTH, level.getBlockState(pos.north()).is(this)).setValue(SOUTH, level.getBlockState(pos.south()).is(this))
                .setValue(EAST, level.getBlockState(pos.east()).is(this)).setValue(WEST, level.getBlockState(pos.west()).is(this));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connected(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return dir.getAxis().isHorizontal() ? connected(state, level, pos) : state;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KoiPondBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.koi_pond").withStyle(ChatFormatting.GRAY));
    }
}
