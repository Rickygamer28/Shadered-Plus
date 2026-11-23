package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Constellation Board: a panel of night sky for a wall (client/ConstellationBoardRenderer,
 * shaders/core/constellation). Right-click a spot to put a star there (joined to the star picked before it); click a
 * star to pick it, then another to join them (or part them, if they're joined); sneak and click a star to take it
 * away. Boards side by side share one night.
 */
public class ConstellationBoardBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ConstellationBoardBlock> CODEC = simpleCodec(ConstellationBoardBlock::new);
    /** Spots across and up a board. */
    public static final int GRID = 8;
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            SHAPES[d.get2DDataValue()] = switch (d) {
                case NORTH -> box(0, 0, 14, 16, 16, 16);
                case SOUTH -> box(0, 0, 0, 16, 16, 2);
                case WEST -> box(14, 0, 0, 16, 16, 16);
                default -> box(0, 0, 0, 2, 16, 16);
            };
        }
    }

    public ConstellationBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    /** Which spot (x + y * GRID) a point on the board's face is, or -1. */
    public static int spotAt(BlockState state, BlockPos pos, Vec3 at) {
        Direction f = state.getValue(FACING), right = f.getCounterClockWise();
        double rx = at.x - pos.getX() - 0.5, rz = at.z - pos.getZ() - 0.5;
        double u = rx * right.getStepX() + rz * right.getStepZ() + 0.5, v = at.y - pos.getY();
        // (the night fills the board inside its frame: 1/16 to 15/16)
        u = (u - 1.0 / 16) / (14.0 / 16);
        v = (v - 1.0 / 16) / (14.0 / 16);
        if (u < 0 || u >= 1 || v < 0 || v >= 1) {
            return -1;
        }
        return (int) (u * GRID) + (int) (v * GRID) * GRID;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity board) || hit.getDirection() != state.getValue(FACING)) {
            return InteractionResult.PASS;
        }
        int spot = spotAt(state, pos, hit.getLocation());
        if (spot < 0) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            float pitch = 1.0F;
            if (board.star(spot)) {
                if (player.isShiftKeyDown()) {
                    board.remove(spot);
                    pitch = 0.6F;
                } else if (board.picked < 0 || board.picked == spot) {
                    board.picked = board.picked == spot ? -1 : spot;
                    pitch = 1.4F;
                } else {
                    board.toggleLine(board.picked, spot);
                    board.picked = spot;
                    pitch = 1.8F;
                }
            } else if (!player.isShiftKeyDown()) {
                board.stars |= 1L << spot;
                if (board.picked >= 0) {
                    board.toggleLine(board.picked, spot);
                }
                board.picked = spot;
                pitch = 1.2F;
            }
            board.changed();
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, pitch);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    /** The board's stars (a bit for each spot), the lines joining them (pairs of spots), and the picked star. */
    public static class Entity extends BlockEntity {
        public long stars;
        public final List<int[]> lines = new ArrayList<>();
        public int picked = -1;

        public Entity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.CONSTELLATION_BOARD.get(), pos, state);
        }

        public boolean star(int spot) {
            return (stars >>> spot & 1L) != 0;
        }

        void remove(int spot) {
            stars &= ~(1L << spot);
            lines.removeIf(l -> l[0] == spot || l[1] == spot);
            if (picked == spot) {
                picked = -1;
            }
        }

        void toggleLine(int a, int b) {
            if (!lines.removeIf(l -> l[0] == a && l[1] == b || l[0] == b && l[1] == a) && lines.size() < 96) {
                lines.add(new int[] {a, b});
            }
        }

        void changed() {
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putLong("stars", stars);
            int[] l = new int[lines.size()];
            for (int i = 0; i < l.length; i++) {
                l[i] = lines.get(i)[0] << 8 | lines.get(i)[1];
            }
            tag.putIntArray("lines", l);
            tag.putInt("picked", picked);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            stars = tag.getLong("stars");
            lines.clear();
            for (int v : tag.getIntArray("lines")) {
                int a = v >> 8 & 255, b = v & 255;
                if (a < GRID * GRID && b < GRID * GRID) {
                    lines.add(new int[] {a, b});
                }
            }
            picked = tag.contains("picked") ? tag.getInt("picked") : -1;
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return saveWithoutMetadata(registries);
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }
}
