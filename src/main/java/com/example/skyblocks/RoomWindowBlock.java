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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A room window: a window in a wall with a whole room behind it - furnished, lit at night - that isn't there: the block
 * is one block deep, the room drawn behind its glass by a shader (client/RoomWindowRenderer), seen properly from any
 * angle. FACING: the way it looks out. ROOM: which room (0: chosen by where it is; right-click for the next). LIGHTS:
 * 0 on at night (as people do), 1 always on, 2 always off (sneak + right-click).
 */
public class RoomWindowBlock extends BaseEntityBlock {
    public static final MapCodec<RoomWindowBlock> CODEC = simpleCodec(RoomWindowBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** How many rooms there are to choose from (ROOM 1 .. ROOMS; 0: one picked by where it is). */
    public static final int ROOMS = 8;
    public static final IntegerProperty ROOM = IntegerProperty.create("room", 0, ROOMS);
    public static final IntegerProperty LIGHTS = IntegerProperty.create("lights", 0, 2);

    public RoomWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ROOM, 0).setValue(LIGHTS, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ROOM, LIGHTS);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // (looking out towards whoever placed it)
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                int lights = (state.getValue(LIGHTS) + 1) % 3;
                level.setBlock(pos, state.setValue(LIGHTS, lights), Block.UPDATE_CLIENTS);
                player.displayClientMessage(Component.translatable("message.skyblocks.room_window.lights." + lights), true);
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, 1.4F);
            } else {
                int room = (state.getValue(ROOM) + 1) % (ROOMS + 1);
                level.setBlock(pos, state.setValue(ROOM, room), Block.UPDATE_CLIENTS);
                player.displayClientMessage(Component.translatable("message.skyblocks.room_window.room." + room), true);
                level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.4F, 1.2F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SimpleRenderedBlockEntity(ModBlockEntities.ROOM_WINDOW.get(), pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.room_window").withStyle(ChatFormatting.GRAY));
    }
}
