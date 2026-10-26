package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A classic brick hearth, 2 wide and 2 tall: brick surround, mantel, logs on embers and real-looking flames
 * (client/WallFireRenderer). The lower right block (as you face it) is the main one: it keeps the colour and draws the
 * fire; clicking any part works on the hearth. Right-click: on / off; with a dye: flame colour; sneak: RGB; with
 * planks or logs: the mantel's wood; with any other full block: the stone.
 */
public class BrickHearthBlock extends BaseEntityBlock {
    public static final MapCodec<BrickHearthBlock> CODEC = simpleCodec(BrickHearthBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** The left column (as you face it); the right column holds the main block. */
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public BrickHearthBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, true).setValue(LEFT, false)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, LEFT, HALF);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** A box in the north-facing frame (px), turned to face the way the hearth does. */
    private static VoxelShape box(Direction f, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (f) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction f = state.getValue(FACING);
        boolean left = state.getValue(LEFT);
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return Shapes.or(box(f, 0, 0, 4, 16, 12, 16), box(f, 0, 12, 2, 16, 14, 16));
        }
        // the lower blocks: the hearth floor, the pillar at the side, the back
        return left ? Shapes.or(box(f, 0, 0, 4, 16, 4, 16), box(f, 10, 4, 4, 16, 16, 16), box(f, 0, 4, 14, 10, 16, 16))
                : Shapes.or(box(f, 0, 0, 4, 16, 4, 16), box(f, 0, 4, 4, 6, 16, 16), box(f, 6, 4, 14, 16, 16, 16));
    }

    /** The lower right block, where the colour is kept. */
    public static BlockPos main(BlockState state, BlockPos pos) {
        BlockPos p = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        return state.getValue(LEFT) ? p.relative(state.getValue(FACING).getCounterClockWise()) : p;
    }

    private static List<BlockPos> parts(Direction f, BlockPos main) {
        BlockPos l = main.relative(f.getClockWise());
        return List.of(main, l, main.above(), l.above());
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction f = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (pos.getY() >= level.getMaxBuildHeight() - 1) {
            return null;
        }
        for (BlockPos p : parts(f, pos)) {
            if (!p.equals(pos) && !level.getBlockState(p).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, f);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        List<BlockPos> ps = parts(state.getValue(FACING), pos);
        level.setBlock(ps.get(1), state.setValue(LEFT, true), Block.UPDATE_ALL);
        level.setBlock(ps.get(2), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        level.setBlock(ps.get(3), state.setValue(LEFT, true).setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        Direction f = state.getValue(FACING);
        boolean left = state.getValue(LEFT);
        DoubleBlockHalf half = state.getValue(HALF);
        boolean sideways = dir == (left ? f.getCounterClockWise() : f.getClockWise());
        boolean vertical = dir == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (!sideways && !vertical) {
            return state;
        }
        boolean ok = next.is(this) && next.getValue(FACING) == f
                && (sideways ? next.getValue(LEFT) != left && next.getValue(HALF) == half
                             : next.getValue(LEFT) == left && next.getValue(HALF) != half);
        return ok ? state.setValue(LIT, next.getValue(LIT)) : Blocks.AIR.defaultBlockState(); // (a part is gone: all of it goes)
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos main = main(state, pos);
        if (!level.isClientSide && !main.equals(pos) && level.getBlockState(main).is(this)) {
            if (player.isCreative()) {
                level.setBlock(main, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            } else {
                level.destroyBlock(main, true, player); // (the hearth drops from its main block)
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem item && !item.getBlock().defaultBlockState().is(this)) {
            // a block: the hearth's wood (planks, logs) or stone (any full solid block) takes its look
            BlockState look = item.getBlock().defaultBlockState();
            boolean isWood = look.is(net.minecraft.tags.BlockTags.PLANKS) || look.is(net.minecraft.tags.BlockTags.LOGS);
            if (!isWood && !look.isSolidRender(level, pos)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                for (BlockPos p : parts(state.getValue(FACING), main(state, pos))) {
                    if (level.getBlockEntity(p) instanceof BrickHearthBlockEntity part) {
                        part.setMaterials(isWood ? part.stone() : look, isWood ? look : part.wood());
                    }
                }
                level.playSound(null, pos, look.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!(stack.getItem() instanceof DyeItem dye) || !(level.getBlockEntity(main(state, pos)) instanceof GlowColourBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            be.setColour(dye.getDyeColor().getTextureDiffuseColor());
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
            BlockPos main = main(state, pos);
            if (player.isShiftKeyDown()) {
                if (level.getBlockEntity(main) instanceof GlowColourBlockEntity be) {
                    be.setRgb(!be.rgb());
                    player.displayClientMessage(Component.translatable(be.rgb() ? "message.skyblocks.rgb_on" : "message.skyblocks.rgb_off")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
            } else {
                boolean lit = !state.getValue(LIT);
                for (BlockPos p : parts(state.getValue(FACING), main)) {
                    BlockState o = level.getBlockState(p);
                    if (o.is(this)) {
                        level.setBlock(p, o.setValue(LIT, lit), Block.UPDATE_CLIENTS);
                    }
                }
                level.playSound(null, pos, lit ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.0F);
                if (lit) {
                    level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.35F, 0.6F);
                }
                if (level instanceof net.minecraft.server.level.ServerLevel server) {
                    // sparks as it catches; a puff of smoke rolling out of the opening as it goes out
                    Direction f = state.getValue(FACING);
                    Direction across = f.getClockWise();
                    double cx = main.getX() + 0.5 + across.getStepX() * 0.5 + f.getStepX() * 0.15;
                    double cz = main.getZ() + 0.5 + across.getStepZ() * 0.5 + f.getStepZ() * 0.15;
                    if (lit) {
                        server.sendParticles(ParticleTypes.FLAME, cx, main.getY() + 0.45, cz, 14, 0.25, 0.08, 0.12, 0.01);
                        server.sendParticles(ParticleTypes.LAVA, cx, main.getY() + 0.4, cz, 4, 0.3, 0.05, 0.1, 0.0);
                    } else {
                        server.sendParticles(ParticleTypes.LARGE_SMOKE, cx, main.getY() + 0.6, cz, 10, 0.35, 0.15, 0.12, 0.01);
                        server.sendParticles(ParticleTypes.SMOKE, cx, main.getY() + 0.9, cz, 16, 0.35, 0.3, 0.12, 0.02);
                    }
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || state.getValue(LEFT) || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        Direction f = state.getValue(FACING);
        Direction across = f.getClockWise();
        double cx = pos.getX() + 0.5 + across.getStepX() * 0.5 - f.getStepX() * 0.1;
        double cz = pos.getZ() + 0.5 + across.getStepZ() * 0.5 - f.getStepZ() * 0.1;
        if (random.nextInt(10) == 0) {
            level.playLocalSound(cx, pos.getY() + 0.5, cz, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.6F + random.nextFloat() * 0.4F, 0.7F + random.nextFloat() * 0.5F, false);
        }
        if (random.nextInt(3) == 0) { // (sparks off the logs)
            double s = (random.nextDouble() - 0.5) * 0.9;
            level.addParticle(ParticleTypes.SMALL_FLAME, cx + across.getStepX() * s, pos.getY() + 0.4 + random.nextDouble() * 0.3,
                    cz + across.getStepZ() * s, 0.0, 0.01, 0.0);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrickHearthBlockEntity(pos, state); // (every part: the materials; the main one also the flame)
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.brick_hearth").withStyle(ChatFormatting.GRAY));
    }
}
