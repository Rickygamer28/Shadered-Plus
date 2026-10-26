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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A stone waterfall for the edge of a koi pond: a mossy pile of rocks, water pouring off a ledge near its top into
 * the pond in front of it (client/PondWaterfallRenderer), splashing where it lands, with the sound of falling water.
 * Facing: the way the water pours (towards a pond beside it when placed, if there is one).
 */
public class PondWaterfallBlock extends BaseEntityBlock {
    public static final MapCodec<PondWaterfallBlock> CODEC = simpleCodec(PondWaterfallBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // (built facing north - pouring towards -z - then turned)
            SHAPES[d.get2DDataValue()] = Shapes.or(box(d, 0, 0, 0, 16, 12, 7), box(d, 0, 0, 7, 16, 18, 16), box(d, 2, 18, 9, 14, 24, 16));
        }
    }

    private static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    public PondWaterfallBlock(Properties properties) {
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
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        for (Direction d : Direction.Plane.HORIZONTAL) { // (pouring into a pond beside it, if there is one)
            if (context.getLevel().getBlockState(context.getClickedPos().relative(d)).getBlock() instanceof KoiPondBlock) {
                facing = d;
                break;
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PondWaterfallBlockEntity(pos, state);
    }

    /**
     * Where the water lands (how high, in world blocks): the pond's water in front, or any water there, or the ground
     * (a little way down at most).
     */
    public static double landing(BlockGetter level, BlockPos pos, Direction facing) {
        BlockPos front = pos.relative(facing);
        for (int down = 0; down < 4; down++) {
            BlockPos p = front.below(down);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof KoiPondBlock) {
                return p.getY() + 14.5 / 16.0;
            }
            FluidState f = s.getFluidState();
            if (!f.isEmpty()) {
                return p.getY() + f.getHeight(level, p);
            }
            if (!s.isAir() && !s.getCollisionShape(level, p).isEmpty()) {
                return p.getY() + s.getCollisionShape(level, p).max(Direction.Axis.Y);
            }
        }
        return front.getY() - 3.0;
    }

    /** Splashes where it lands, and its sound. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        double land = landing(level, pos, facing);
        double fx = pos.getX() + 0.5 + facing.getStepX() * 0.84, fz = pos.getZ() + 0.5 + facing.getStepZ() * 0.84;
        for (int i = 0; i < 3; i++) {
            double sx = (random.nextDouble() - 0.5) * 0.3, sz = (random.nextDouble() - 0.5) * 0.3;
            double ax = facing.getAxis() == Direction.Axis.X ? 0.0 : sx, az = facing.getAxis() == Direction.Axis.Z ? 0.0 : sz;
            level.addParticle(ParticleTypes.SPLASH, fx + ax + facing.getStepX() * sx * 0.3, land + 0.05, fz + az + facing.getStepZ() * sz * 0.3,
                    0.0, 0.05, 0.0);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.BUBBLE_POP, fx, land + 0.02, fz, (random.nextDouble() - 0.5) * 0.05, 0.02, (random.nextDouble() - 0.5) * 0.05);
        }
        if (random.nextInt(14) == 0) {
            level.playLocalSound(fx, land, fz, SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT, 0.35F, 0.8F + random.nextFloat() * 0.3F, false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.pond_waterfall").withStyle(ChatFormatting.GRAY));
    }
}
