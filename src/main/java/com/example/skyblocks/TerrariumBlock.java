package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
 * A pet terrarium: a glass tank with a little desert (sand, rocks, a cactus) or jungle (moss, a branch, leaves, a water
 * dish) and two geckos living in it - wandering, stopping to look round, flicking their tongues (client/TerrariumRenderer).
 * Right-click: the other scene. Tanks side by side join into one bigger tank.
 */
public class TerrariumBlock extends BaseEntityBlock {
    public static final MapCodec<TerrariumBlock> CODEC = simpleCodec(TerrariumBlock::new);
    public static final IntegerProperty SCENE = IntegerProperty.create("scene", 0, 1);
    /** Joined to the tank on that side: one bigger tank (no glass between, the geckos free to wander across). */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty NORTH = net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH,
            SOUTH = net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH,
            EAST = net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST,
            WEST = net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public TerrariumBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SCENE, 0).setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SCENE, NORTH, SOUTH, EAST, WEST);
    }

    public static net.minecraft.world.level.block.state.properties.BooleanProperty side(net.minecraft.core.Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            default -> WEST;
        };
    }

    private BlockState join(BlockState state, net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            state = state.setValue(side(d), level.getBlockState(pos.relative(d)).getBlock() instanceof TerrariumBlock);
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState s = defaultBlockState();
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) { // (the scene of a tank it joins)
            BlockState n = context.getLevel().getBlockState(context.getClickedPos().relative(d));
            if (n.getBlock() instanceof TerrariumBlock) {
                s = s.setValue(SCENE, n.getValue(SCENE));
                break;
            }
        }
        return join(s, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.core.Direction dir, BlockState next, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return dir.getAxis().isHorizontal() ? join(state, level, pos) : state;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int scene = 1 - state.getValue(SCENE);
            java.util.ArrayDeque<BlockPos> todo = new java.util.ArrayDeque<>();
            java.util.Set<BlockPos> seen = new java.util.HashSet<>();
            todo.add(pos);
            seen.add(pos);
            while (!todo.isEmpty() && seen.size() < 256) { // (the whole joined tank)
                BlockPos p = todo.poll();
                level.setBlock(p, level.getBlockState(p).setValue(SCENE, scene), Block.UPDATE_ALL);
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos n = p.relative(d);
                    if (!seen.contains(n) && level.getBlockState(n).getBlock() instanceof TerrariumBlock) {
                        seen.add(n);
                        todo.add(n);
                    }
                }
            }
            level.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.7F, 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerrariumBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.terrarium").withStyle(ChatFormatting.GRAY));
    }
}
