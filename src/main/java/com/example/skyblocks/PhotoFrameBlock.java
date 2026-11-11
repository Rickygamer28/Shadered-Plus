package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A photo frame, three sizes, hung on a wall or standing on the ground. Small: one block on a wall, a desk frame standing;
 * medium (light wood): two blocks wide on a wall, on an easel standing; large (gilded): three wide on a wall, a tall easel.
 * Placed empty from a frame item (PhotoFrameItem) - right-click it with a photo to put it in, an empty hand to take it out
 * - or straight from a photo (a small frame, which goes when its photo's taken). FACING: the way the picture faces.
 * The bigger frames and the pictures are client/PhotoFrameRenderer; the small frames are block models.
 */
public class PhotoFrameBlock extends BaseEntityBlock {
    public static final MapCodec<PhotoFrameBlock> CODEC = simpleCodec(PhotoFrameBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty STANDING = BooleanProperty.create("standing");

    public enum Size implements StringRepresentable {
        SMALL("small"), MEDIUM("medium"), LARGE("large");

        private final String name;

        Size(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Size> SIZE = EnumProperty.create("size", Size.class);

    private static final Map<Direction, VoxelShape> WALL = Map.of(
            Direction.NORTH, Block.box(1, 2.5, 14.5, 15, 13.5, 16), Direction.SOUTH, Block.box(1, 2.5, 0, 15, 13.5, 1.5),
            Direction.WEST, Block.box(14.5, 2.5, 1, 16, 13.5, 15), Direction.EAST, Block.box(0, 2.5, 1, 1.5, 13.5, 15));
    private static final Map<Direction, VoxelShape> WALL_BIG = Map.of(
            Direction.NORTH, Block.box(0, 0, 14, 16, 16, 16), Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 2),
            Direction.WEST, Block.box(14, 0, 0, 16, 16, 16), Direction.EAST, Block.box(0, 0, 0, 2, 16, 16));
    private static final Map<Direction, VoxelShape> STAND = Map.of(
            Direction.NORTH, Block.box(3, 0, 6.5, 13, 9, 11), Direction.SOUTH, Block.box(3, 0, 5, 13, 9, 9.5),
            Direction.WEST, Block.box(6.5, 0, 3, 11, 9, 13), Direction.EAST, Block.box(5, 0, 3, 9.5, 9, 13));
    private static final VoxelShape EASEL = Block.box(1, 0, 1, 15, 16, 15);

    public PhotoFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STANDING, false).setValue(SIZE, Size.SMALL));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STANDING, SIZE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean small = state.getValue(SIZE) == Size.SMALL;
        if (state.getValue(STANDING)) {
            return small ? STAND.get(state.getValue(FACING)) : EASEL;
        }
        return (small ? WALL : WALL_BIG).get(state.getValue(FACING));
    }

    /** On a wall: something solid behind it. Standing: something to stand on. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(STANDING)) {
            return Block.canSupportCenter(level, pos.below(), Direction.UP);
        }
        BlockPos behind = pos.relative(state.getValue(FACING).getOpposite());
        return level.getBlockState(behind).isSolid();
    }

    /**
     * Room for a frame here: a big one on a wall covers the 3 x 3 in front of the wall round it, standing it reaches up
     * into the block above; none of it may be solid, or another frame.
     */
    public static boolean roomFor(Level level, BlockPos pos, Direction facing, boolean standing, Size size) {
        if (size == Size.SMALL) {
            return true;
        }
        if (standing) {
            return free(level, pos.above());
        }
        Direction side = facing.getClockWise();
        for (int up = -1; up <= 1; up++) {
            for (int across = -1; across <= 1; across++) {
                BlockPos at = pos.relative(side, across).above(up);
                if (!at.equals(pos) && !free(level, at)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean free(Level level, BlockPos at) {
        BlockState s = level.getBlockState(at);
        return !(s.getBlock() instanceof PhotoFrameBlock) && s.getCollisionShape(level, at).isEmpty();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** A photo: into the frame (swapping out the one there). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        ModComponents.Photo photo = stack.get(ModComponents.PHOTO.get());
        if (!(stack.getItem() instanceof PhotoItem) || photo == null || !(level.getBlockEntity(pos) instanceof PhotoFrameBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            ItemStack old = be.photo() != null ? be.photoStack() : ItemStack.EMPTY;
            be.setPhoto(photo);
            stack.consume(1, player);
            if (!old.isEmpty() && !player.getInventory().add(old)) {
                player.drop(old, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** An empty hand: the photo out (a frame that came with its photo goes with it). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PhotoFrameBlockEntity be) || be.photo() == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack photo = be.photoStack();
            if (be.framed()) {
                be.setPhoto(null);
            } else {
                be.forget();
                level.removeBlock(pos, false);
            }
            if (!player.getInventory().add(photo)) {
                player.drop(photo, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The photo back, and the frame if it was one (however it went: broken, or what held it up gone). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof PhotoFrameBlockEntity be) {
            if (be.photo() != null) {
                Block.popResource(level, pos, be.photoStack());
            }
            if (be.framed()) {
                Block.popResource(level, pos, new ItemStack(frameItem(state.getValue(SIZE))));
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    public static net.minecraft.world.item.Item frameItem(Size size) {
        return switch (size) {
            case MEDIUM -> ModItems.MEDIUM_PHOTO_FRAME.get();
            case LARGE -> ModItems.LARGE_PHOTO_FRAME.get();
            default -> ModItems.SMALL_PHOTO_FRAME.get();
        };
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof PhotoFrameBlockEntity be && !be.framed() && be.photo() != null) {
            return be.photoStack();
        }
        return new ItemStack(frameItem(state.getValue(SIZE)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotoFrameBlockEntity(pos, state);
    }
}
