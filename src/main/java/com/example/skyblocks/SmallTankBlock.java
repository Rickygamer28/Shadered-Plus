package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The small fish tanks (client/SmallTankRenderer, shaders/core/small_tank):
 * <ul>
 * <li>BOWL - a fishbowl: a round glass bowl on its own;</li>
 * <li>TALL - a tall cylinder tank: stacked, they make one taller glass column;</li>
 * <li>SLIM - a slim wall tank: hung on a wall; side by side and on top of each other they make one wider, taller tank.</li>
 * </ul>
 * Right-click: the next set of fish.
 */
public class SmallTankBlock extends BaseEntityBlock {
    public static final int BOWL = 0, TALL = 1, SLIM = 2;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    /** The biggest a joined tank gets (blocks, each way). */
    public static final int MAX = 8;
    private static final VoxelShape BOWL_SHAPE = Block.box(2, 0, 2, 14, 12, 14);
    private static final VoxelShape TALL_SHAPE = Block.box(1, 0, 1, 15, 16, 15);
    private static final VoxelShape SLIM_S = Block.box(0, 0, 0, 16, 16, 5);
    private static final VoxelShape SLIM_N = Block.box(0, 0, 11, 16, 16, 16);
    private static final VoxelShape SLIM_W = Block.box(11, 0, 0, 16, 16, 16);
    private static final VoxelShape SLIM_E = Block.box(0, 0, 0, 5, 16, 16);

    private final int shape;
    private final MapCodec<SmallTankBlock> codec;

    public SmallTankBlock(Properties properties, int shape) {
        super(properties);
        this.shape = shape;
        this.codec = simpleCodec(p -> new SmallTankBlock(p, shape));
        BlockState s = stateDefinition.any();
        if (shape == TALL) s = s.setValue(UP, false).setValue(DOWN, false);
        if (shape == SLIM) s = s.setValue(FACING, Direction.SOUTH);
        registerDefaultState(s);
    }

    public int shape() {
        return shape;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // (called from the constructor before shape is set: every kind gets the properties, only some use them)
        builder.add(FACING, UP, DOWN);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (shape == BOWL) return BOWL_SHAPE;
        if (shape == TALL) return TALL_SHAPE;
        return switch (state.getValue(FACING)) {
            case NORTH -> SLIM_N;
            case WEST -> SLIM_W;
            case EAST -> SLIM_E;
            default -> SLIM_S;
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = defaultBlockState();
        if (shape == TALL) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            s = s.setValue(UP, level.getBlockState(pos.above()).is(this)).setValue(DOWN, level.getBlockState(pos.below()).is(this));
        }
        if (shape == SLIM) {
            Direction face = context.getClickedFace();
            s = s.setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
        }
        return s;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        if (shape == TALL && dir == Direction.UP) return state.setValue(UP, next.is(this));
        if (shape == TALL && dir == Direction.DOWN) return state.setValue(DOWN, next.is(this));
        return state;
    }

    private static boolean joins(BlockState a, BlockState b) {
        return b.getBlock() == a.getBlock() && (!(a.getBlock() instanceof SmallTankBlock t) || t.shape != SLIM || b.getValue(FACING) == a.getValue(FACING));
    }

    /**
     * The tank this block is part of: its corner block (the bottom one; for a slim tank the bottom left, seen from in
     * front) and its size - {x, y, z, width, height}.
     */
    public static int[] group(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof SmallTankBlock t) || t.shape == BOWL) {
            return new int[] {pos.getX(), pos.getY(), pos.getZ(), 1, 1};
        }
        BlockPos a = pos;
        for (int i = 0; i < MAX * 2 && joins(state, level.getBlockState(a.below())); i++) a = a.below();
        if (t.shape == TALL) {
            int h = 1;
            while (h < MAX && joins(state, level.getBlockState(a.above(h)))) h++;
            return new int[] {a.getX(), a.getY(), a.getZ(), 1, h};
        }
        Direction facing = state.getValue(FACING);
        Direction left = facing.getClockWise(), right = facing.getCounterClockWise();
        for (int i = 0; i < MAX * 2 && joins(state, level.getBlockState(a.relative(left))); i++) a = a.relative(left);
        int w = 1, h = 1;
        while (w < MAX && joins(state, level.getBlockState(a.relative(right, w)))) w++;
        while (h < MAX && joins(state, level.getBlockState(a.above(h)))) h++;
        return new int[] {a.getX(), a.getY(), a.getZ(), w, h};
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int[] g = group(level, pos, state);
            if (level.getBlockEntity(new BlockPos(g[0], g[1], g[2])) instanceof SmallTankBlockEntity be) {
                be.cycle();
                player.displayClientMessage(Component.translatable("message.skyblocks.small_tank." + shape + "." + be.variant())
                        .withStyle(ChatFormatting.AQUA), true);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.BLOCKS, 0.6F, 1.1F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmallTankBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.small_tank." + shape).withStyle(ChatFormatting.GRAY));
    }
}
