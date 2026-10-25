package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
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
 * A modern wall fire: a glass-fronted strip of flame over white stones. Placed side by side (facing the same way) they
 * join into one long fire. Right-click: on / off; with a dye: the flame's colour; sneak + right-click: RGB. The flames
 * jump with music. Drawn by client/WallFireRenderer (shaders/core/wall_fire) in its black frame.
 */
public class WallFireBlock extends BaseEntityBlock {
    public static final MapCodec<WallFireBlock> CODEC = simpleCodec(WallFireBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Another wall fire facing the same way on its left / right (as you face it): no end of the frame there. */
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    public WallFireBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, true).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, LEFT, RIGHT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> Block.box(0, 1, 0, 16, 15, 7.3);
            case EAST -> Block.box(0, 1, 0, 7.3, 15, 16);
            case WEST -> Block.box(8.7, 1, 0, 16, 15, 16);
            default -> Block.box(0, 1, 8.7, 16, 15, 16);
        };
    }

    private boolean joins(BlockState other, Direction facing) {
        return other.is(this) && other.getValue(FACING) == facing;
    }

    private BlockState connected(BlockState state, LevelAccessor level, BlockPos pos) {
        Direction f = state.getValue(FACING);
        return state.setValue(LEFT, joins(level.getBlockState(pos.relative(f.getClockWise())), f))
                .setValue(RIGHT, joins(level.getBlockState(pos.relative(f.getCounterClockWise())), f));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connected(defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return dir.getAxis().isHorizontal() ? connected(state, level, pos) : state;
    }

    /** This fire and every one joined to it in its row (the whole long fire). */
    private List<BlockPos> row(Level level, BlockPos pos, BlockState state) {
        Direction f = state.getValue(FACING);
        List<BlockPos> out = new java.util.ArrayList<>();
        out.add(pos);
        for (Direction d : new Direction[] {f.getClockWise(), f.getCounterClockWise()}) {
            BlockPos p = pos.relative(d);
            for (int i = 0; i < 64 && joins(level.getBlockState(p), f); i++) {
                out.add(p);
                p = p.relative(d);
            }
        }
        return out;
    }

    /** A new fire added to a row takes the row's colour, RGB and on / off. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof GlowColourBlockEntity be)) {
            return;
        }
        Direction f = state.getValue(FACING);
        for (Direction d : new Direction[] {f.getClockWise(), f.getCounterClockWise()}) {
            BlockState o = level.getBlockState(pos.relative(d));
            if (joins(o, f) && level.getBlockEntity(pos.relative(d)) instanceof GlowColourBlockEntity other) {
                be.setColour(other.colour());
                be.setRgb(other.rgb());
                level.setBlock(pos, level.getBlockState(pos).setValue(LIT, o.getValue(LIT)), Block.UPDATE_ALL);
                return;
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye) || !(level.getBlockEntity(pos) instanceof GlowColourBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            for (BlockPos p : row(level, pos, state)) { // (the whole long fire takes the colour)
                if (level.getBlockEntity(p) instanceof GlowColourBlockEntity other) {
                    other.setColour(dye.getDyeColor().getTextureDiffuseColor());
                }
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                if (level.getBlockEntity(pos) instanceof GlowColourBlockEntity be) {
                    boolean rgb = !be.rgb();
                    for (BlockPos p : row(level, pos, state)) {
                        if (level.getBlockEntity(p) instanceof GlowColourBlockEntity other) {
                            other.setRgb(rgb);
                        }
                    }
                    player.displayClientMessage(Component.translatable(rgb ? "message.skyblocks.rgb_on" : "message.skyblocks.rgb_off")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
            } else {
                boolean lit = !state.getValue(LIT);
                for (BlockPos p : row(level, pos, state)) {
                    BlockState o = level.getBlockState(p);
                    level.setBlock(p, o.setValue(LIT, lit), Block.UPDATE_ALL);
                }
                level.playSound(null, pos, state.getValue(LIT) ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.FLINTANDSTEEL_USE,
                        SoundSource.BLOCKS, 0.5F, 1.0F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && random.nextInt(24) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                    0.4F + random.nextFloat() * 0.3F, 0.8F + random.nextFloat() * 0.3F, false);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return GlowColourBlockEntity.wallFire(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.wall_fire").withStyle(ChatFormatting.GRAY));
    }
}
