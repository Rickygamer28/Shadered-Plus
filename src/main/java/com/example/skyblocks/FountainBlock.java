package com.example.skyblocks;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
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
 * The fountains: the Water Fountain (in HD water, shaders/core/hd_water) and the Sky Fountain (of sky liquid; its sky
 * kept in a SkyHolderBlockEntity). Fountain blocks side by side join into one big basin - a rectangle of them, up to
 * 9 by 9 - with one statue in its middle (bigger, the bigger the basin): a two-tiered fountain, its bowl brimming over
 * in thin sheets, a jet leaping from its top and falling in arcs into the pool (client/FountainRenderer).
 */
public class FountainBlock extends Block implements EntityBlock {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH,
            WEST = BlockStateProperties.WEST;
    public static final int MAX = 9;
    private static final VoxelShape[] SHAPES = new VoxelShape[16];

    static {
        for (int i = 0; i < 16; i++) {
            VoxelShape s = box(0, 0, 0, 16, 2, 16);
            if ((i & 1) == 0) s = Shapes.or(s, box(0, 2, 0, 16, 8, 3)); // (north rim)
            if ((i & 2) == 0) s = Shapes.or(s, box(13, 2, 0, 16, 8, 16)); // (east)
            if ((i & 4) == 0) s = Shapes.or(s, box(0, 2, 13, 16, 8, 16)); // (south)
            if ((i & 8) == 0) s = Shapes.or(s, box(0, 2, 0, 3, 8, 16)); // (west)
            SHAPES[i] = s;
        }
    }

    private final boolean sky;

    public FountainBlock(Properties properties, boolean sky) {
        super(properties);
        this.sky = sky;
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    public static FountainBlock water(Properties properties) {
        return new FountainBlock(properties, false);
    }

    public static FountainBlock sky(Properties properties) {
        return new FountainBlock(properties, true);
    }

    public boolean isSky() {
        return sky;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    private static BooleanProperty side(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = defaultBlockState();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            s = s.setValue(side(d), context.getLevel().getBlockState(context.getClickedPos().relative(d)).is(this));
        }
        return s;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction.getAxis().isHorizontal() ? state.setValue(side(direction), neighbour.is(this)) : state;
    }

    private static int mask(BlockState s) {
        return (s.getValue(NORTH) ? 1 : 0) | (s.getValue(EAST) ? 2 : 0) | (s.getValue(SOUTH) ? 4 : 0) | (s.getValue(WEST) ? 8 : 0);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[mask(state)];
    }

    /**
     * The basin a fountain block is part of: its corners (min x, min z, max x, max z) - walked along the joins from
     * this block (the basin's meant to be a rectangle). Its north-west block draws the statue and the water's leaps.
     */
    public static int[] basin(BlockGetter level, BlockPos pos) {
        int x0 = pos.getX(), z0 = pos.getZ();
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0; i < MAX - 1 && joined(level, p.set(x0, pos.getY(), z0), WEST); i++) x0--;
        for (int i = 0; i < MAX - 1 && joined(level, p.set(x0, pos.getY(), z0), NORTH); i++) z0--;
        int x1 = x0, z1 = z0;
        for (int i = 0; i < MAX - 1 && joined(level, p.set(x1, pos.getY(), z0), EAST); i++) x1++;
        for (int i = 0; i < MAX - 1 && joined(level, p.set(x0, pos.getY(), z1), SOUTH); i++) z1++;
        return new int[] {x0, z0, x1, z1};
    }

    private static boolean joined(BlockGetter level, BlockPos p, BooleanProperty side) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof FountainBlock && s.getValue(side);
    }

    /** The statue's size for a basin (w x d blocks): 0.8 in a single block, up to 2.4. */
    public static float statue(int w, int d) {
        int n = Math.min(w, d);
        return n <= 1 ? 0.8F : Math.min(2.4F, 0.8F + 0.45F * (n - 1));
    }

    /** How far from the basin's middle the arcs land: between the statue's bowl and the rim. */
    public static float landing(int w, int d) {
        float half = Math.min(w, d) * 0.5F - 3.0F / 16.0F, bowl = 0.3F * statue(w, d);
        return bowl + (half - bowl) * 0.6F;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        SkyblockType type = sky ? SkyLiquidBucketRecipe.skyOf(stack) : null;
        if (type == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            // (the whole basin takes the sky)
            int[] b = basin(level, pos);
            for (int x = b[0]; x <= b[2]; x++) {
                for (int z = b[1]; z <= b[3]; z++) {
                    if (level.getBlockEntity(new BlockPos(x, pos.getY(), z)) instanceof SkyHolderBlockEntity holder) {
                        holder.setSky(type);
                    }
                }
            }
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.1F);
            player.displayClientMessage(Component.translatable("message.skyblocks.sky_fountain.sky", stack.getHoverName()), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(sky ? "tooltip.skyblocks.sky_fountain" : "tooltip.skyblocks.water_fountain").withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return sky ? SkyHolderBlockEntity.fountain(pos, state) : new SimpleRenderedBlockEntity(ModBlockEntities.WATER_FOUNTAIN.get(), pos, state);
    }

    /** Small splashes where the arcs land (from the basin's north-west block), and now and then its murmur. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(NORTH) || state.getValue(WEST)) {
            return;
        }
        int[] b = basin(level, pos);
        int w = b[2] - b[0] + 1, d = b[3] - b[1] + 1;
        double cx = b[0] + w * 0.5, cz = b[1] + d * 0.5;
        float r = landing(w, d);
        int arcs = w * d > 1 ? 12 : 8;
        for (int i = 0; i < 2; i++) {
            float a = random.nextInt(arcs) * Mth.TWO_PI / arcs + 0.2F;
            level.addParticle(sky ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.SPLASH, cx + Mth.cos(a) * r, pos.getY() + 0.4, cz + Mth.sin(a) * r, 0.0, 0.0, 0.0);
        }
        if (random.nextInt(14) == 0) {
            level.playLocalSound(cx, pos.getY() + 0.5, cz, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.2F + 0.05F * Math.min(w, d),
                    1.2F + random.nextFloat() * 0.3F, false);
        }
    }
}
