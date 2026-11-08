package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import com.example.skyblocks.compat.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A magma basin: a stone basin of slowly churning lava - dark crust plates drifting apart and crunching together,
 * glowing cracks between them, bubbles swelling and popping (client/MagmaBasinRenderer, shaders/core/magma_basin).
 * Basins side by side join into one pool. Hot to stand in; with a dye: the lava's colour.
 */
public class MagmaBasinBlock extends BaseEntityBlock {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    /** The lava's surface, block units. */
    public static final float SURFACE = 12.0F / 16.0F;
    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 11, 16);

    public MagmaBasinBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    private static BooleanProperty side(Direction dir) {
        return switch (dir) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            state = state.setValue(side(dir), context.getLevel().getBlockState(context.getClickedPos().relative(dir)).is(this));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return dir.getAxis().isHorizontal() ? state.setValue(side(dir), next.is(this)) : state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = FLOOR;
        if (!state.getValue(NORTH)) shape = Shapes.or(shape, Block.box(0, 0, 0, 16, 14, 2));
        if (!state.getValue(SOUTH)) shape = Shapes.or(shape, Block.box(0, 0, 14, 16, 14, 16));
        if (!state.getValue(WEST)) shape = Shapes.or(shape, Block.box(0, 0, 0, 2, 14, 16));
        if (!state.getValue(EAST)) shape = Shapes.or(shape, Block.box(14, 0, 0, 16, 14, 16));
        return shape;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        // like a magma block: it burns what stands in it (not sneaking players, not the fire-proof)
        if (!entity.isSteppingCarefully() && entity instanceof LivingEntity && !entity.fireImmune()) {
            entity.hurt(level.damageSources().hotFloor(), 1.0F);
            entity.setSecondsOnFire(3);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double y = pos.getY() + SURFACE + 0.02;
        if (random.nextInt(14) == 0) { // (a bubble popping: a spark, sometimes a lava drop)
            double x = pos.getX() + 0.2 + random.nextDouble() * 0.6, z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
            level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.SMALL_FLAME, x, y, z, 0.0, 0.02, 0.0);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), y + 0.05, pos.getZ() + random.nextDouble(), 0.0, 0.015, 0.0);
        }
        if (random.nextInt(140) == 0) {
            level.playLocalSound(pos.getX() + 0.5, y, pos.getZ() + 0.5, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS,
                    0.25F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
        }
        if (random.nextInt(60) == 0) {
            level.playLocalSound(pos.getX() + 0.5, y, pos.getZ() + 0.5, SoundEvents.LAVA_POP, SoundSource.BLOCKS,
                    0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
        }
    }

    protected com.example.skyblocks.compat.ItemInteractionResult useItemOn121(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye) || !(level.getBlockEntity(pos) instanceof GlowColourBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            be.setColour(com.example.skyblocks.compat.Compat.dyeColour(dye.getDyeColor()));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return GlowColourBlockEntity.magmaBasin(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.magma_basin").withStyle(ChatFormatting.GRAY));
    }

    protected net.minecraft.world.InteractionResult useWithoutItem121(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        return net.minecraft.world.InteractionResult.PASS;
    }

    /** 1.20.1: one use() - an item first (useItemOn121), then the empty-hand use (useWithoutItem121), as 1.21 does. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                                      net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                                      net.minecraft.world.phys.BlockHitResult hit) {
        com.example.skyblocks.compat.ItemInteractionResult r = useItemOn121(player.getItemInHand(hand), state, level, pos, player, hand, hit);
        if (r != com.example.skyblocks.compat.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return r.result();
        }
        return hand == net.minecraft.world.InteractionHand.MAIN_HAND ? useWithoutItem121(state, level, pos, player, hit) : net.minecraft.world.InteractionResult.PASS;
    }
}
