package com.example.skyblocks;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A bamboo water spout (a shishi-odoshi) for beside a pond: a bamboo pipe trickles water into a rocking bamboo tube;
 * as it fills it tips forward and pours into the pond in front, then swings back and knocks against its stone with a
 * hollow clack. Facing: the way it pours (towards a pond beside it, or one a block down beside it, when placed). The
 * tube and the water are drawn by client/BambooSpoutRenderer; its clack and splash come from BambooSpoutBlockEntity.
 */
public class BambooSpoutBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // (built facing north - pouring towards -z - then turned)
            SHAPES[d.get2DDataValue()] = Shapes.or(box(d, 4, 0, 10.5, 12, 3, 15.5), box(d, 3, 0, 7, 13, 10, 9), box(d, 6.5, 3, 0, 9.5, 12, 16),
                    box(d, 12.5, 0, 0.5, 14.5, 15.5, 2.5));
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

    public BambooSpoutBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        for (Direction d : Direction.Plane.HORIZONTAL) { // (pouring into a pond beside it - or a block down beside it - if there is one)
            BlockPos n = context.getClickedPos().relative(d);
            if (context.getLevel().getBlockState(n).getBlock() instanceof KoiPondBlock
                    || context.getLevel().getBlockState(n.below()).getBlock() instanceof KoiPondBlock) {
                facing = d;
                break;
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    // ---- its rocking: the same on every computer (from the game's time and where it is) ----

    /** How long one round takes: filling, tipping, pouring, swinging back. */
    public static final double CYCLE = 8.0;
    public static final double TIP = 6.2, POUR = 6.55, BACK = 7.3, CLACK = 7.6;

    /** Where in its round it is now (seconds, 0 .. CYCLE). */
    public static double phase(BlockPos pos, double seconds) {
        double off = KoiPondBlock.u01(KoiPondBlock.hashu(Math.floorMod(pos.getX(), 512), pos.getY(), Math.floorMod(pos.getZ(), 512))) * CYCLE;
        double p = (seconds + off) % CYCLE;
        return p < 0.0 ? p + CYCLE : p;
    }

    /**
     * How the tube is tilted (degrees: + its open end up, - down). Filling, it slowly leans further forward; full, it
     * tips quickly, pours a moment, swings back up and knocks on its stone, bouncing a little.
     */
    public static float angle(double p) {
        double rest = 19.5;
        if (p < TIP) return (float) (rest - 6.0 * smooth(0.0, TIP, p));
        if (p < POUR) { double k = (p - TIP) / (POUR - TIP); return (float) (rest - 6.0 - (rest - 6.0 + 25.0) * k * k); }
        if (p < BACK) return (float) (-25.0 + 1.5 * Math.sin((p - POUR) * 14.0) * Math.exp(-(p - POUR) * 4.0));
        if (p < CLACK) { double k = (p - BACK) / (CLACK - BACK); return (float) (-25.0 + (rest + 25.0) * k * k); }
        double k = p - CLACK;
        return (float) (rest - 4.0 * Math.abs(Math.sin(k * 11.0)) * Math.exp(-k * 7.0));
    }

    private static double smooth(double a, double b, double x) {
        double k = Math.max(0.0, Math.min(1.0, (x - a) / (b - a)));
        return k * k * (3.0 - 2.0 * k);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BambooSpoutBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, ModBlockEntities.BAMBOO_SPOUT.get(), BambooSpoutBlockEntity::clientTick) : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.bamboo_spout").withStyle(ChatFormatting.GRAY));
    }
}
