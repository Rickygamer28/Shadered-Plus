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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Neon tubes, laid flat on whatever they're placed on - floors, walls, ceilings, several in one block space (a floor's
 * and a wall's): they join up with the tubes next to them on the same surface, bend into the corner where a floor meets
 * a wall, and wrap over edges - up walls, across ceilings (client/NeonTubeRenderer). A dye recolours the whole joined
 * run (sneak: just this block).
 */
public class NeonTubeBlock extends BaseEntityBlock {
    public static final MapCodec<NeonTubeBlock> CODEC = simpleCodec(NeonTubeBlock::new);
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    /** A tube on that side of the space: laid on the block that way (DOWN: on the floor). */
    public static final Map<Direction, BooleanProperty> FACES = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.DOWN, Block.box(0, 0, 0, 16, 3, 16), Direction.UP, Block.box(0, 13, 0, 16, 16, 16),
            Direction.NORTH, Block.box(0, 0, 0, 16, 16, 3), Direction.SOUTH, Block.box(0, 0, 13, 16, 16, 16),
            Direction.WEST, Block.box(0, 0, 0, 3, 16, 16), Direction.EAST, Block.box(13, 0, 0, 16, 16, 16));

    public NeonTubeBlock(Properties properties) {
        super(properties);
        BlockState s = stateDefinition.any().setValue(COLOR, DyeColor.PINK);
        for (BooleanProperty p : FACES.values()) {
            s = s.setValue(p, false);
        }
        registerDefaultState(s.setValue(FACES.get(Direction.DOWN), true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
        FACES.values().forEach(builder::add);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED; // (drawn by its renderer: how it joins depends on its neighbours)
    }

    public static boolean has(BlockState state, Direction face) {
        return state.getBlock() instanceof NeonTubeBlock && state.getValue(FACES.get(face));
    }

    /** Another tube can go in this space if it's on a side that has none yet. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().is(asItem()) && !state.getValue(FACES.get(context.getClickedFace().getOpposite()))
                && !context.isSecondaryUseActive();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace().getOpposite();
        BlockState here = context.getLevel().getBlockState(context.getClickedPos());
        if (here.getBlock() instanceof NeonTubeBlock) {
            return here.setValue(FACES.get(face), true);
        }
        BlockState s = defaultBlockState().setValue(FACES.get(Direction.DOWN), false).setValue(FACES.get(face), true);
        for (Direction d : Direction.values()) { // (the colour of a tube next to it)
            BlockState n = context.getLevel().getBlockState(context.getClickedPos().relative(d));
            if (n.getBlock() instanceof NeonTubeBlock) {
                return s.setValue(COLOR, n.getValue(COLOR));
            }
        }
        return s;
    }

    /** Which way a lone tube on the side just added lies: along the way you were looking (sneaking: up a wall). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof NeonTubeBlockEntity be && placer != null) {
            for (Direction face : Direction.values()) {
                if (state.getValue(FACES.get(face)) && !be.known(face)) {
                    Direction.Axis along;
                    if (face.getAxis() == Direction.Axis.Y) {
                        along = placer.getDirection().getAxis();
                    } else {
                        along = placer.isShiftKeyDown() ? Direction.Axis.Y : face.getClockWise().getAxis();
                    }
                    be.setAxis(face, along);
                }
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = Shapes.empty();
        for (Direction d : Direction.values()) {
            if (state.getValue(FACES.get(d))) s = Shapes.or(s, SHAPES.get(d));
        }
        return s.isEmpty() ? SHAPES.get(Direction.DOWN) : s;
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
            // the whole joined run - tubes touching it, edges and corners included (sneaking: just this one)
            Set<BlockPos> seen = new HashSet<>();
            ArrayDeque<BlockPos> todo = new ArrayDeque<>();
            todo.add(pos);
            seen.add(pos);
            while (!todo.isEmpty() && seen.size() <= 2048) {
                BlockPos p = todo.poll();
                level.setBlock(p, level.getBlockState(p).setValue(COLOR, colour), Block.UPDATE_CLIENTS);
                if (player.isShiftKeyDown()) break;
                for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                    if (!seen.contains(n) && level.getBlockState(n).getBlock() instanceof NeonTubeBlock) {
                        BlockPos im = n.immutable();
                        seen.add(im);
                        todo.add(im);
                    }
                }
            }
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NeonTubeBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.neon_tube").withStyle(ChatFormatting.GRAY));
    }
}
