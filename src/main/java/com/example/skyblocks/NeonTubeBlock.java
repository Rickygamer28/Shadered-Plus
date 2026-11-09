package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A neon tube: a thin glowing glass tube that joins to the tubes next to it in any direction - straight runs, corners,
 * T's and crosses - to draw shapes on walls and ceilings. On its own it lies along the way it was placed. A dye recolours
 * the whole joined run (sneak: just this one).
 */
public class NeonTubeBlock extends Block {
    public static final MapCodec<NeonTubeBlock> CODEC = simpleCodec(NeonTubeBlock::new);
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape CORE = Block.box(6.5, 6.5, 6.5, 9.5, 9.5, 9.5);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.NORTH, Block.box(6.5, 6.5, 0, 9.5, 9.5, 6.5), Direction.SOUTH, Block.box(6.5, 6.5, 9.5, 9.5, 9.5, 16),
            Direction.WEST, Block.box(0, 6.5, 6.5, 6.5, 9.5, 9.5), Direction.EAST, Block.box(9.5, 6.5, 6.5, 16, 9.5, 9.5),
            Direction.DOWN, Block.box(6.5, 0, 6.5, 9.5, 6.5, 9.5), Direction.UP, Block.box(6.5, 9.5, 6.5, 9.5, 16, 9.5));

    public NeonTubeBlock(Properties properties) {
        super(properties);
        BlockState s = stateDefinition.any().setValue(COLOR, DyeColor.PINK).setValue(AXIS, Direction.Axis.X);
        for (BooleanProperty p : SIDES.values()) {
            s = s.setValue(p, false);
        }
        registerDefaultState(s);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR, AXIS);
        SIDES.values().forEach(builder::add);
    }

    /** The arms shown: towards each tube next to it; alone, both ways along its axis; with one, straight on through. */
    private BlockState connect(BlockState state, LevelAccessor level, BlockPos pos) {
        int n = 0;
        Direction only = null;
        for (Direction d : Direction.values()) {
            boolean tube = level.getBlockState(pos.relative(d)).getBlock() instanceof NeonTubeBlock;
            state = state.setValue(SIDES.get(d), tube);
            if (tube) {
                n++;
                only = d;
            }
        }
        if (n == 0) {
            Direction.Axis a = state.getValue(AXIS);
            state = state.setValue(SIDES.get(Direction.fromAxisAndDirection(a, Direction.AxisDirection.POSITIVE)), true)
                    .setValue(SIDES.get(Direction.fromAxisAndDirection(a, Direction.AxisDirection.NEGATIVE)), true);
        } else if (n == 1) {
            state = state.setValue(SIDES.get(only.getOpposite()), true).setValue(AXIS, only.getAxis());
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = context.getClickedFace().getAxis() == Direction.Axis.Y
                ? context.getHorizontalDirection().getAxis() : context.getClickedFace().getClockWise().getAxis();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            axis = Direction.Axis.Y; // (sneaking: standing up)
        }
        BlockState s = defaultBlockState().setValue(AXIS, axis);
        // (the colour of a tube it's joined to)
        for (Direction d : Direction.values()) {
            BlockState n = context.getLevel().getBlockState(context.getClickedPos().relative(d));
            if (n.getBlock() instanceof NeonTubeBlock) {
                s = s.setValue(COLOR, n.getValue(COLOR));
                break;
            }
        }
        return connect(s, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return connect(state, level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = CORE;
        for (Direction d : Direction.values()) {
            if (state.getValue(SIDES.get(d))) {
                s = Shapes.or(s, ARMS.get(d));
            }
        }
        return s;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            DyeColor colour = dye.getDyeColor();
            // the whole joined run (sneaking: just this tube)
            Set<BlockPos> seen = new HashSet<>();
            ArrayDeque<BlockPos> todo = new ArrayDeque<>();
            todo.add(pos);
            seen.add(pos);
            while (!todo.isEmpty() && seen.size() <= 1024) {
                BlockPos p = todo.poll();
                BlockState s = level.getBlockState(p);
                level.setBlock(p, s.setValue(COLOR, colour), Block.UPDATE_CLIENTS);
                if (player.isShiftKeyDown()) {
                    break;
                }
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (!seen.contains(n) && level.getBlockState(n).getBlock() instanceof NeonTubeBlock) {
                        seen.add(n);
                        todo.add(n);
                    }
                }
            }
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.neon_tube").withStyle(ChatFormatting.GRAY));
    }
}
