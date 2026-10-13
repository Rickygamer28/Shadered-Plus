package com.example.skyblocks.island;

import com.example.skyblocks.ModComponents;
import com.example.skyblocks.ModItems;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Island Painting: a flat frame on a wall, like a painting. Right-click it with a Sky Island and the picture
 * becomes a window into a little room inside the wall with the island in it, mobs and all
 * (client/island/IslandWindow); right-click with an empty hand to take the island out. It uses the Sky Island's
 * block entity to hold the island.
 */
public class IslandPaintingBlock extends BaseEntityBlock {
    public static final MapCodec<IslandPaintingBlock> CODEC = simpleCodec(IslandPaintingBlock::new);
    /** The way the painting faces (away from the wall it hangs on). */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /**
     * Joined to the painting above / below / on its clockwise / counter-clockwise side (seen from above), when the
     * paintings make a full rectangle of 2x2 up to 8x8 on the same wall: then the frame is only drawn on the outside
     * and the bottom counter-clockwise corner block draws one big window for all of them.
     */
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty CW = BooleanProperty.create("cw");
    public static final BooleanProperty CCW = BooleanProperty.create("ccw");
    public static final int MIN_JOIN = 2;
    public static final int MAX_JOIN = 8;

    /** The frame for each facing: flat against the wall, 1 pixel thick, like a painting. */
    private static final VoxelShape NORTH = Block.box(0, 0, 15, 16, 16, 16);
    private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, 1);
    private static final VoxelShape WEST = Block.box(15, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST = Block.box(0, 0, 0, 1, 16, 16);

    public IslandPaintingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(UP, false).setValue(DOWN, false).setValue(CW, false).setValue(CCW, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, UP, DOWN, CW, CCW);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkyIslandBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    // ---- hanging on walls ----

    /** Only on the side of a block, like a painting. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isVertical()) {
            return null;
        }
        BlockState state = defaultBlockState().setValue(FACING, face);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }

    /** Falls off (dropping everything) when its wall is removed. */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ---- joining into bigger paintings ----

    /** The block that holds and draws a joined painting (its bottom counter-clockwise corner); itself if alone. */
    public static BlockPos anchor(BlockGetter level, BlockPos pos, BlockState state) {
        Direction back = state.getValue(FACING).getCounterClockWise();
        BlockPos p = pos;
        BlockState s = state;
        for (int i = 0; i < MAX_JOIN && s.getValue(CCW); i++) {
            p = p.relative(back);
            s = level.getBlockState(p);
            if (!(s.getBlock() instanceof IslandPaintingBlock)) {
                return pos;
            }
        }
        for (int i = 0; i < MAX_JOIN && s.getValue(DOWN); i++) {
            p = p.below();
            s = level.getBlockState(p);
            if (!(s.getBlock() instanceof IslandPaintingBlock)) {
                return pos;
            }
        }
        return p;
    }

    /** How many blocks wide / tall the painting is, from its anchor (1 if alone). */
    public static int width(BlockGetter level, BlockPos anchor, BlockState state) {
        Direction cw = state.getValue(FACING).getClockWise();
        int n = 1;
        BlockState s = state;
        BlockPos p = anchor;
        while (n < MAX_JOIN && s.getBlock() instanceof IslandPaintingBlock && s.getValue(CW)) {
            p = p.relative(cw);
            s = level.getBlockState(p);
            n++;
        }
        return n;
    }

    public static int height(BlockGetter level, BlockPos anchor, BlockState state) {
        int n = 1;
        BlockState s = state;
        BlockPos p = anchor;
        while (n < MAX_JOIN && s.getBlock() instanceof IslandPaintingBlock && s.getValue(UP)) {
            p = p.above();
            s = level.getBlockState(p);
            n++;
        }
        return n;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            updateGroup(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this)) {
            Direction facing = state.getValue(FACING);
            for (Direction d : new Direction[] {Direction.UP, Direction.DOWN, facing.getClockWise(), facing.getCounterClockWise()}) {
                BlockPos next = pos.relative(d);
                BlockState other = level.getBlockState(next);
                if (other.is(this) && other.getValue(FACING) == facing) {
                    updateGroup(level, next);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Finds the paintings touching this one on the same wall (same facing) and joins them if they make a full
     * rectangle of 2x2 to 8x8; otherwise they are all separate.
     */
    private void updateGroup(Level level, BlockPos start) {
        BlockState first = level.getBlockState(start);
        if (!first.is(this)) {
            return;
        }
        Direction facing = first.getValue(FACING);
        Direction cw = facing.getClockWise();
        Direction[] around = {Direction.UP, Direction.DOWN, cw, cw.getOpposite()};
        java.util.Set<BlockPos> group = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        group.add(start);
        queue.add(start);
        boolean tooBig = false;
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : around) {
                BlockPos next = p.relative(d);
                if (group.contains(next)) {
                    continue;
                }
                BlockState s = level.getBlockState(next);
                if (s.is(this) && s.getValue(FACING) == facing) {
                    if (group.size() >= MAX_JOIN * MAX_JOIN) {
                        tooBig = true;
                        break;
                    }
                    group.add(next);
                    queue.add(next);
                }
            }
        }
        int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE, minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;
        for (BlockPos p : group) {
            int u = along(p, start, cw), v = p.getY() - start.getY();
            minU = Math.min(minU, u); maxU = Math.max(maxU, u);
            minV = Math.min(minV, v); maxV = Math.max(maxV, v);
        }
        int w = maxU - minU + 1, h = maxV - minV + 1;
        boolean joined = !tooBig && group.size() == w * h && w >= MIN_JOIN && h >= MIN_JOIN && w <= MAX_JOIN && h <= MAX_JOIN;
        for (BlockPos p : group) {
            BlockState s = level.getBlockState(p);
            int u = along(p, start, cw), v = p.getY() - start.getY();
            BlockState joinedState = s.setValue(UP, joined && v < maxV).setValue(DOWN, joined && v > minV)
                    .setValue(CW, joined && u < maxU).setValue(CCW, joined && u > minU);
            if (joinedState != s) {
                level.setBlock(p, joinedState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    /** Position of p along the wall (towards cw), relative to start. */
    private static int along(BlockPos p, BlockPos start, Direction cw) {
        return cw.getStepX() * (p.getX() - start.getX()) + cw.getStepZ() * (p.getZ() - start.getZ());
    }

    // ---- putting an island in / taking it out (any block of a joined painting works on the whole painting) ----

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(anchor(level, pos, state)) instanceof SkyIslandBlockEntity painting)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // A Shadered sky block (Shadered's or ours, filters included): becomes the room's background.
        if (ShaderedSkyItems.isSkyBlock(stack)) {
            if (!level.isClientSide) {
                ItemStack old = painting.background();
                painting.setBackground(stack.copyWithCount(1));
                stack.consume(1, player);
                if (!old.isEmpty()) {
                    give(player, old);
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.2F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        IslandData island = stack.get(ModComponents.ISLAND.get());
        if (island == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (painting.island() != null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // already has one: take it out first
        }
        if (!level.isClientSide) {
            painting.setIsland(island);
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()
                || !(level.getBlockEntity(anchor(level, pos, state)) instanceof SkyIslandBlockEntity painting)
                || (painting.island() == null && painting.background().isEmpty())) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            // sneak + empty hand: turn the island 45 degrees
            if (painting.island() == null) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                painting.rotate();
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            // the island comes out first, then the background
            if (painting.island() != null) {
                ItemStack island = islandItem(painting);
                painting.setIsland(null);
                give(player, island);
            } else {
                ItemStack background = painting.background();
                painting.setBackground(ItemStack.EMPTY);
                give(player, background);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static ItemStack islandItem(SkyIslandBlockEntity painting) {
        ItemStack stack = new ItemStack(ModItems.SKY_ISLAND.get());
        stack.set(ModComponents.ISLAND.get(), painting.island());
        return stack;
    }

    /** The painting, plus the island and background inside it (no loot table: the island comes from the block entity). */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new ArrayList<>();
        drops.add(new ItemStack(ModItems.ISLAND_PAINTING.get()));
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof SkyIslandBlockEntity painting) {
            if (painting.island() != null) {
                drops.add(islandItem(painting));
            }
            if (!painting.background().isEmpty()) {
                drops.add(painting.background().copy());
            }
        }
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.ISLAND_PAINTING.get());
    }
}
