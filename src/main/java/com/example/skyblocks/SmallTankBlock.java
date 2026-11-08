package com.example.skyblocks;

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
 * <li>TALL - a tall cylinder tank: stacked, they make one taller glass column; a square of them (up to 4 x 4) a wider
 * one, with bigger fish the wider it is;</li>
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
    /** The widest a tall tank gets (blocks: a square this wide). */
    public static final int WIDE = 4;
    private static final VoxelShape BOWL_SHAPE = Block.box(2, 0, 2, 14, 12, 14);
    private static final VoxelShape TALL_SHAPE = Block.box(1, 0, 1, 15, 16, 15);
    private static final VoxelShape SLIM_S = Block.box(0, 0, 0, 16, 16, 5);
    private static final VoxelShape SLIM_N = Block.box(0, 0, 11, 16, 16, 16);
    private static final VoxelShape SLIM_W = Block.box(11, 0, 0, 16, 16, 16);
    private static final VoxelShape SLIM_E = Block.box(0, 0, 0, 5, 16, 16);

    private final int shape;

    public SmallTankBlock(Properties properties, int shape) {
        super(properties);
        this.shape = shape;
        BlockState s = stateDefinition.any();
        if (shape == TALL) s = s.setValue(UP, false).setValue(DOWN, false);
        if (shape == SLIM) s = s.setValue(FACING, Direction.SOUTH);
        registerDefaultState(s);
    }

    public int shape() {
        return shape;
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // (called from the constructor before shape is set: every kind gets the properties, only some use them)
        builder.add(FACING, UP, DOWN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
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
        if (shape == SLIM) {
            Direction face = context.getClickedFace();
            s = s.setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
        }
        return s;
    }

    // ---- the tall tank's rims: a base under the bottom block of each tank, a cap on its top - where a column is
    // taller than a tank can be, it's split into tanks of MAX blocks (from the bottom up), each with its own base and
    // cap. Adding or taking away a block moves those splits, so the whole column is looked at again ----

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (shape == TALL && !level.isClientSide && !old.is(this)) {
            refreshColumn(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        super.onRemove(state, level, pos, now, moving);
        if (shape == TALL && !level.isClientSide && !now.is(this)) {
            if (level.getBlockState(pos.above()).is(this)) refreshColumn(level, pos.above());
            if (level.getBlockState(pos.below()).is(this)) refreshColumn(level, pos.below());
        }
    }

    /** Sets the base and cap of every block in this column of tall tanks. */
    private void refreshColumn(Level level, BlockPos pos) {
        BlockPos b = pos;
        for (int i = 0; i < COLUMN && level.getBlockState(b.below()).is(this); i++) b = b.below();
        for (int i = 0; i < COLUMN; i++) {
            BlockPos p = b.above(i);
            BlockState st = level.getBlockState(p);
            if (!st.is(this)) break;
            boolean above = level.getBlockState(p.above()).is(this);
            BlockState want = st.setValue(DOWN, i % MAX != 0).setValue(UP, above && i % MAX != MAX - 1);
            if (want != st) level.setBlock(p, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        if (shape == TALL && dir.getAxis() == Direction.Axis.Y) {
            int i = 0;
            for (BlockPos b = pos; i < COLUMN && level.getBlockState(b.below()).is(this); b = b.below()) i++;
            boolean above = level.getBlockState(pos.above()).is(this);
            return state.setValue(DOWN, i % MAX != 0).setValue(UP, above && i % MAX != MAX - 1);
        }
        return state;
    }

    private static boolean joins(BlockState a, BlockState b) {
        return b.getBlock() == a.getBlock() && (!(a.getBlock() instanceof SmallTankBlock t) || t.shape != SLIM || b.getValue(FACING) == a.getValue(FACING));
    }

    /** The longest column looked at (blocks). */
    private static final int COLUMN = 512;
    /** The most columns side by side looked at, to share them out into tanks. */
    private static final int SPREAD = 400;

    // (worked out once a tick on the client: every block's renderer asks)
    private static final java.util.Map<Long, int[]> CACHE = new java.util.HashMap<>();
    private static Object cacheLevel;
    private static long cacheTick = Long.MIN_VALUE;

    /**
     * A column of blocks split into tanks of up to MAX: {the bottom of the tank this block is in, its height}.
     * {@code y} is the block's height; {@code at} steps along the column (x, z fixed).
     */
    private static int[] span(BlockGetter level, BlockState state, BlockPos pos) {
        int i = 0;
        BlockPos b = pos;
        while (i < COLUMN && joins(state, level.getBlockState(b.below()))) {
            b = b.below();
            i++;
        }
        int y0 = pos.getY() - i % MAX;
        int h = 1;
        while (h < MAX && joins(state, level.getBlockState(new BlockPos(pos.getX(), y0 + h, pos.getZ())))) h++;
        return new int[] {y0, h};
    }

    /**
     * The tank this block is part of: its corner block (the bottom one; for a slim tank the bottom left, seen from in
     * front) and its size - {x, y, z, width, height}. Blocks join only into whole tanks no bigger than they can be:
     * a column taller than MAX is split into tanks MAX high (from the bottom up); columns side by side (of the same
     * height, from the same height) are shared out into squares at most WIDE across (a slim tank: rows at most MAX
     * long), whatever's left over making tanks of its own.
     */
    public static int[] group(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof SmallTankBlock t) || t.shape == BOWL) {
            return new int[] {pos.getX(), pos.getY(), pos.getZ(), 1, 1};
        }
        int[] sp = span(level, state, pos);
        boolean cache = level instanceof Level l && l.isClientSide();
        long key = new BlockPos(pos.getX(), sp[0], pos.getZ()).asLong();
        if (cache) {
            long tick = ((Level) level).getGameTime();
            if (cacheLevel != level || cacheTick != tick) {
                CACHE.clear();
                cacheLevel = level;
                cacheTick = tick;
            }
            int[] g = CACHE.get(key);
            if (g != null) return g;
        }
        int[] g = t.shape == TALL ? tallGroup(level, state, pos, sp, cache) : slimGroup(level, state, pos, sp);
        if (cache) CACHE.put(key, g);
        return g;
    }

    /** A column's piece at (x, y0, z) of the same height and from the same height as this one: joined to it. */
    private static boolean sameSpan(BlockGetter level, BlockState state, int x, int y0, int z, int h) {
        BlockPos p = new BlockPos(x, y0, z);
        if (!joins(state, level.getBlockState(p))) return false;
        int[] o = span(level, state, p);
        return o[0] == y0 && o[1] == h;
    }

    private static int[] tallGroup(BlockGetter level, BlockState state, BlockPos pos, int[] sp, boolean cache) {
        int y0 = sp[0], h = sp[1];
        // the columns joined to this one (side by side, of the same span)
        java.util.Set<Long> cells = new java.util.HashSet<>();
        java.util.ArrayDeque<long[]> todo = new java.util.ArrayDeque<>();
        cells.add(xz(pos.getX(), pos.getZ()));
        todo.add(new long[] {pos.getX(), pos.getZ()});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!todo.isEmpty() && cells.size() < SPREAD) {
            long[] c = todo.poll();
            for (int[] st : steps) {
                int x = (int) c[0] + st[0], z = (int) c[1] + st[1];
                long k = xz(x, z);
                if (cells.contains(k) || !sameSpan(level, state, x, y0, z, h)) continue;
                cells.add(k);
                todo.add(new long[] {x, z});
            }
        }
        // shared out into squares: north to south, west to east, each the biggest square that fits from there
        long[] order = new long[cells.size()];
        int n = 0;
        for (long k : cells) order[n++] = k;
        java.util.Arrays.sort(order, 0, n);
        java.util.Set<Long> used = new java.util.HashSet<>();
        int[] mine = null;
        for (int i = 0; i < n; i++) {
            if (used.contains(order[i])) continue;
            int x = xzX(order[i]), z = xzZ(order[i]);
            int s = 1;
            grow:
            while (s < WIDE) {
                for (int d = 0; d <= s; d++) { // (the next row and column: all there, and free)
                    long a = xz(x + s, z + d), b = xz(x + d, z + s);
                    if (!cells.contains(a) || used.contains(a) || !cells.contains(b) || used.contains(b)) break grow;
                }
                s++;
            }
            int[] g = {x, y0, z, s, h};
            for (int dz = 0; dz < s; dz++) {
                for (int dx = 0; dx < s; dx++) {
                    used.add(xz(x + dx, z + dz));
                    if (cache) CACHE.put(new BlockPos(x + dx, y0, z + dz).asLong(), g);
                    if (x + dx == pos.getX() && z + dz == pos.getZ()) mine = g;
                }
            }
        }
        return mine != null ? mine : new int[] {pos.getX(), y0, pos.getZ(), 1, h};
    }

    // a column's place as one number, in order north to south, then west to east
    private static long xz(int x, int z) {
        return (long) z * 0x1_0000_0000L + x;
    }

    private static int xzZ(long k) {
        return (int) Math.floorDiv(k + 0x8000_0000L, 0x1_0000_0000L);
    }

    private static int xzX(long k) {
        return (int) (k - (long) xzZ(k) * 0x1_0000_0000L);
    }

    private static int[] slimGroup(BlockGetter level, BlockState state, BlockPos pos, int[] sp) {
        int y0 = sp[0], h = sp[1];
        Direction facing = state.getValue(FACING);
        Direction left = facing.getClockWise(), right = facing.getCounterClockWise();
        // the row along the wall, from its left end, split into tanks at most MAX long
        BlockPos at = new BlockPos(pos.getX(), y0, pos.getZ());
        int i = 0;
        while (i < COLUMN) {
            BlockPos n = at.relative(left);
            if (!sameSpan(level, state, n.getX(), y0, n.getZ(), h)) break;
            at = n;
            i++;
        }
        BlockPos a = at.relative(right, i - i % MAX);
        int w = 1;
        while (w < MAX) {
            BlockPos n = a.relative(right, w);
            if (!sameSpan(level, state, n.getX(), y0, n.getZ(), h)) break;
            w++;
        }
        return new int[] {a.getX(), y0, a.getZ(), w, h};
    }

    protected net.minecraft.world.InteractionResult useWithoutItem121(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int[] g = group(level, pos, state);
            if (level.getBlockEntity(new BlockPos(g[0], g[1], g[2])) instanceof SmallTankBlockEntity be) {
                be.cycle();
                String key = shape == TALL ? "message.skyblocks.tall_tank." + Math.min(g[3], WIDE) + "." + be.variant()
                        : "message.skyblocks.small_tank." + shape + "." + be.variant();
                player.displayClientMessage(Component.translatable(key)
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
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.small_tank." + shape).withStyle(ChatFormatting.GRAY));
    }

    protected com.example.skyblocks.compat.ItemInteractionResult useItemOn121(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        return com.example.skyblocks.compat.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
