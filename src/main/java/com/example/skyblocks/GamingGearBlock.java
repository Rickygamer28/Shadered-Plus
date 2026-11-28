package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Gaming gear: a PC tower (its fans and the inside behind its glass side lit in shifting colours) or a keyboard and
 * mouse (a colour wave across the keys) - both lit brighter with whatever a Gaming Monitor, Television, Projector or
 * Tablet nearby plays, its bass in the fans, its highs at the keyboard's right. Use either to open the nearest
 * monitor's (or television's) window.
 */
public class GamingGearBlock extends BaseEntityBlock {
    public static final MapCodec<GamingGearBlock> TOWER_CODEC = simpleCodec(p -> new GamingGearBlock(p, false));
    public static final MapCodec<GamingGearBlock> KEYBOARD_CODEC = simpleCodec(p -> new GamingGearBlock(p, true));
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** The tower's: 10 wide, 15 tall, 12 deep (its front to the north); the keyboard's: a thin slab with the mouse. */
    private static final VoxelShape[] TOWER = shapes(3, 0, 2, 13, 15, 14), KEYBOARD = shapes(1, 0, 4, 15.5, 1.5, 11);
    public final boolean keyboard;

    public GamingGearBlock(Properties properties, boolean keyboard) {
        super(properties);
        this.keyboard = keyboard;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static VoxelShape[] shapes(double x0, double y0, double z0, double x1, double y1, double z1) {
        VoxelShape[] out = new VoxelShape[4];
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // (north as given; the others turned about the middle)
            double ax0 = x0, az0 = z0, ax1 = x1, az1 = z1;
            switch (d) {
                case SOUTH -> { ax0 = 16 - x1; ax1 = 16 - x0; az0 = 16 - z1; az1 = 16 - z0; }
                case EAST -> { ax0 = 16 - z1; ax1 = 16 - z0; az0 = x0; az1 = x1; }
                case WEST -> { ax0 = z0; ax1 = z1; az0 = 16 - x1; az1 = 16 - x0; }
                default -> { }
            }
            out[d.get2DDataValue()] = Block.box(ax0, y0, az0, ax1, y1, az1);
        }
        return out;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return keyboard ? KEYBOARD_CODEC : TOWER_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (keyboard ? KEYBOARD : TOWER)[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            com.example.skyblocks.client.ClientAccess.openNearestScreen(pos); // (client-only class, never loaded on a server)
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GamingGearBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(keyboard ? "tooltip.skyblocks.gaming_keyboard" : "tooltip.skyblocks.gaming_pc").withStyle(ChatFormatting.GRAY));
    }
}
