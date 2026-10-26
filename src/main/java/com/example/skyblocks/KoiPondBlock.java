package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A koi pond: clear water with koi swimming lazily under lily pads, light rippling on the pebbles. Ponds side by side
 * join into one bigger pond (no stone rim between them). Drawn by client/KoiPondRenderer (shaders/core/koi_pond).
 */
public class KoiPondBlock extends BaseEntityBlock {
    public static final MapCodec<KoiPondBlock> CODEC = simpleCodec(KoiPondBlock::new);
    /** Another pond on that side: the water carries on there. */
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");

    public KoiPondBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false).setValue(WEST, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = Block.box(0, 0, 0, 16, 1, 16);
        if (!state.getValue(NORTH)) s = Shapes.or(s, Block.box(0, 0, 0, 16, 16, 2));
        if (!state.getValue(SOUTH)) s = Shapes.or(s, Block.box(0, 0, 14, 16, 16, 16));
        if (!state.getValue(WEST)) s = Shapes.or(s, Block.box(0, 0, 0, 2, 16, 16));
        if (!state.getValue(EAST)) s = Shapes.or(s, Block.box(14, 0, 0, 16, 16, 16));
        return s;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(0, 0, 0, 16, 14.5, 16); // (you can walk on the water)
    }

    private BlockState connected(BlockState state, LevelAccessor level, BlockPos pos) {
        return state.setValue(NORTH, level.getBlockState(pos.north()).is(this)).setValue(SOUTH, level.getBlockState(pos.south()).is(this))
                .setValue(EAST, level.getBlockState(pos.east()).is(this)).setValue(WEST, level.getBlockState(pos.west()).is(this));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connected(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return dir.getAxis().isHorizontal() ? connected(state, level, pos) : state;
    }

    // ---- where the frogs are (the same whole-number hash as shaders/core/koi_pond, so the croaks come from them) ----

    public static int hashu(int x, int y, int z) {
        x = x * 1664525 + 1013904223; y = y * 1664525 + 1013904223; z = z * 1664525 + 1013904223;
        x += y * z; y += z * x; z += x * y;
        x ^= x >>> 16; y ^= y >>> 16; z ^= z >>> 16;
        x += y * z; y += z * x; z += x * y;
        return x ^ y ^ z;
    }

    public static float u01(int h) {
        return (h & 65535) / 65535.0F;
    }

    /** Is a frog sitting on this pond block's lily pad now (at most one to a block, coming and going)? */
    public static boolean frogOn(BlockPos pos, double seconds) {
        return frogOn(pos, seconds, false);
    }

    /** stone: a stepping stone on this pond block (the frog sits on that). */
    public static boolean frogOn(BlockPos pos, double seconds, boolean stone) {
        double te = frogTime(pos, seconds, stone);
        return te > 6.0 && te < 70.0;
    }

    /**
     * How far into a frog's visit to this block's lily pad it is now (seconds: hopping on from 5 to 6, sitting, hopping
     * off from 70 to 71), or -1 with no frog here.
     */
    public static double frogTime(BlockPos pos, double seconds) {
        return frogTime(pos, seconds, false);
    }

    /** stone: a stepping stone on this pond block (frogs sit on that, lily pad or not). */
    public static double frogTime(BlockPos pos, double seconds, boolean stone) {
        int cx = Math.floorMod(pos.getX(), 512), cz = Math.floorMod(pos.getZ(), 512);
        if (!stone && (!padHere(pos) || 0.08F + 0.07F * u01(hashu(cx, cz, 100)) < 0.09F)) {
            return -1.0; // (no pad, or too small for a frog)
        }
        float hx = u01(hashu(cx, cz, 200)), hy = u01(hashu(cx, cz, 300));
        double phase = seconds / 75.0 + hx * 13.0;
        int epoch = (int) Math.floor(phase);
        double te = (phase - epoch) * 75.0;
        return (stone || hy < 0.55F) && u01(hashu(cx, cz, 200000 + epoch)) < 0.6F && te > 5.0 && te < 71.0 ? te : -1.0; // (a stone: frogs like it more)
    }

    /** Which frog is visiting now (0 orange temperate, 1 green cold, 2 white warm: as in shaders/core/koi_pond). */
    public static int frogKind(BlockPos pos, double seconds) {
        int cx = Math.floorMod(pos.getX(), 512), cz = Math.floorMod(pos.getZ(), 512);
        int epoch = (int) Math.floor(seconds / 75.0 + u01(hashu(cx, cz, 200)) * 13.0);
        return Math.min((int) (u01(hashu(cx, cz, 500000 + epoch)) * 3.0F), 2);
    }

    /** Has this block a (first) lily pad? */
    public static boolean padHere(BlockPos pos) {
        return u01(hashu(Math.floorMod(pos.getX(), 512), Math.floorMod(pos.getZ(), 512), 0)) >= 0.45F;
    }

    /** The frogs on its lily pads croak now and then; a little splash now and then. */
    @Override
    public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        double seconds = (level.getGameTime() % 72_000L) / 20.0;
        // (only a frog sitting on a lily pad croaks: now and then by day, much more at night - but not with anyone close)
        long dayTime = level.getDayTime() % 24000L;
        boolean night = dayTime > 13000L && dayTime < 23000L;
        boolean near = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3.5, false) != null;
        if (frogOn(pos, seconds, SteppingStoneBlock.on(level, pos)) && !near && random.nextInt(night ? 70 : 400) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, net.minecraft.sounds.SoundEvents.FROG_AMBIENT,
                    net.minecraft.sounds.SoundSource.AMBIENT, 0.5F, 0.9F + random.nextFloat() * 0.2F, false);
        }
        if (random.nextInt(900) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, net.minecraft.sounds.SoundEvents.FISH_SWIM,
                    net.minecraft.sounds.SoundSource.AMBIENT, 0.25F, 1.0F, false);
        }
    }

    /** A vanilla koi pond: its menu (what its rim and floor are made of, for the whole pond). */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                                                                   net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (this != ModBlocks.VANILLA_KOI_POND.get()) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (level.isClientSide) {
            com.example.skyblocks.client.ClientAccess.openVanillaPond(pos); // (client-only class, never loaded on a server)
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KoiPondBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId().replace("block.", "tooltip.")).withStyle(ChatFormatting.GRAY));
    }
}
