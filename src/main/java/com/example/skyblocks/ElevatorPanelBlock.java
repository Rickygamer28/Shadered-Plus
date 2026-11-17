package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * An elevator's call panel: put beside its shaft (2 to 4 blocks from the shaft's middle) on a floor - standing on the
 * floor, or on a wall at eye height - it makes that a floor. Right-click: the cabin's called there (the floor's chosen
 * inside it, on its buttons); sneak + right-click: the elevator's look and speed (client/ElevatorScreen).
 */
public class ElevatorPanelBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ElevatorPanelBlock> CODEC = simpleCodec(ElevatorPanelBlock::new);
    private static final VoxelShape NORTH = Block.box(4, 3, 14, 12, 13, 16), SOUTH = Block.box(4, 3, 0, 12, 13, 2),
            WEST = Block.box(14, 3, 4, 16, 13, 12), EAST = Block.box(0, 3, 4, 2, 13, 12);

    public ElevatorPanelBlock(Properties properties) {
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

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** The floor a panel is on (where you stand, y): on the floor, its own height; on a wall, the one below. */
    public static int landing(BlockGetter level, BlockPos panel) {
        if (!level.getBlockState(panel.below()).getCollisionShape(level, panel.below()).isEmpty()) {
            return panel.getY();
        }
        if (!level.getBlockState(panel.below(2)).getCollisionShape(level, panel.below(2)).isEmpty()) {
            return panel.getY() - 1;
        }
        return panel.getY();
    }

    /** The elevator a panel belongs to: its base, below it, the shaft's middle 2 to 4 blocks from it. */
    @Nullable
    public static ElevatorBaseBlockEntity base(Level level, BlockPos panel) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= ElevatorBaseBlockEntity.REACH + 2; dy++) {
            int y = panel.getY() - dy;
            if (y < level.getMinBuildHeight()) {
                break;
            }
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) {
                        continue;
                    }
                    p.set(panel.getX() + dx, y, panel.getZ() + dz);
                    if (level.getBlockEntity(p) instanceof ElevatorBaseBlockEntity be) {
                        return be;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ElevatorBaseBlockEntity be = base(level, pos);
        if (be == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.skyblocks.elevator.no_base").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                com.example.skyblocks.client.ElevatorScreen.open(be.getBlockPos());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        int landing = landing(level, pos);
        int floor = be.floorAt(landing);
        if (!level.isClientSide) {
            ElevatorCabinEntity cabin = be.cabin();
            boolean here = cabin != null && !cabin.moving() && Math.abs(cabin.landing() - landing) < 0.5F;
            if (!here) {
                be.call(floor);
                player.displayClientMessage(Component.translatable("message.skyblocks.elevator.called", floor + 1).withStyle(ChatFormatting.AQUA), true);
            }
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, 1.4F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(this)) {
            tellBase(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        super.onRemove(state, level, pos, now, moving);
        if (!level.isClientSide && !now.is(this)) {
            tellBase(level, pos);
        }
    }

    /** Its elevator's floors found again (one more, or one less). */
    private static void tellBase(Level level, BlockPos pos) {
        ElevatorBaseBlockEntity be = base(level, pos);
        if (be != null) {
            be.floorsChanged();
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.elevator_panel").withStyle(ChatFormatting.GRAY));
    }
}
