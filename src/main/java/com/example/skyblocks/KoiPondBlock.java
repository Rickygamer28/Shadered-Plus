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

    private static int hashu(int x, int y, int z) {
        x = x * 1664525 + 1013904223; y = y * 1664525 + 1013904223; z = z * 1664525 + 1013904223;
        x += y * z; y += z * x; z += x * y;
        x ^= x >>> 16; y ^= y >>> 16; z ^= z >>> 16;
        x += y * z; y += z * x; z += x * y;
        return x ^ y ^ z;
    }

    private static float u01(int h) {
        return (h & 65535) / 65535.0F;
    }

    /** Is a frog sitting on lily pad k of this pond block now? */
    public static boolean frogOn(BlockPos pos, int k, double seconds) {
        int cx = Math.floorMod(pos.getX(), 512), cz = Math.floorMod(pos.getZ(), 512);
        if (u01(hashu(cx, cz, k)) < 0.45F || 0.08F + 0.07F * u01(hashu(cx, cz, k + 100)) < 0.09F) {
            return false; // (no pad, or too small for a frog)
        }
        float hx = u01(hashu(cx, cz, k + 200)), hy = u01(hashu(cx, cz, k + 300));
        int epoch = (int) Math.floor(seconds / 75.0 + hx * 13.0);
        return hy < 0.4F && u01(hashu(cx, cz, k + 200000 + epoch)) < 0.6F;
    }

    /** The frogs on its lily pads croak now and then; a little splash now and then. */
    @Override
    public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        double seconds = (level.getGameTime() % 72_000L) / 20.0;
        for (int k = 0; k < 2; k++) {
            if (frogOn(pos, k, seconds) && random.nextInt(260) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, net.minecraft.sounds.SoundEvents.FROG_AMBIENT,
                        net.minecraft.sounds.SoundSource.AMBIENT, 0.5F, 0.9F + random.nextFloat() * 0.2F, false);
            }
        }
        if (random.nextInt(900) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, net.minecraft.sounds.SoundEvents.FISH_SWIM,
                    net.minecraft.sounds.SoundSource.AMBIENT, 0.25F, 1.0F, false);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KoiPondBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.koi_pond").withStyle(ChatFormatting.GRAY));
    }
}
