package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A stage spotlight - on a floor, a ceiling or a wall: a lamp on a stand that turns to follow the nearest player (sweeping round when there's nobody),
 * a soft cone of light and a pool of it where it lands (client/SpotlightRenderer). Right-click: on / off; a dye: its
 * colour; glowstone dust: back to white.
 */
public class SpotlightBlock extends BaseEntityBlock {
    public static final MapCodec<SpotlightBlock> CODEC = simpleCodec(SpotlightBlock::new);
    /** The way its stand points out from what it's fixed to: up off a floor, down from a ceiling, out from a wall. */
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
    private static final java.util.Map<net.minecraft.core.Direction, VoxelShape> SHAPES = java.util.Map.of(
            net.minecraft.core.Direction.UP, Block.box(3, 0, 3, 13, 14, 13), net.minecraft.core.Direction.DOWN, Block.box(3, 2, 3, 13, 16, 13),
            net.minecraft.core.Direction.NORTH, Block.box(3, 3, 2, 13, 13, 16), net.minecraft.core.Direction.SOUTH, Block.box(3, 3, 0, 13, 13, 14),
            net.minecraft.core.Direction.WEST, Block.box(2, 3, 3, 16, 13, 13), net.minecraft.core.Direction.EAST, Block.box(0, 3, 3, 14, 13, 13));

    public SpotlightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem) && !stack.is(Items.GLOWSTONE_DUST)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SpotlightBlockEntity be) {
            be.setColour(stack.getItem() instanceof DyeItem dye ? dye.getDyeColor().getTextureDiffuseColor() & 0xFFFFFF : 0xFFF4D8);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SpotlightBlockEntity be) {
            be.setOn(!be.on());
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, be.on() ? 1.0F : 0.7F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpotlightBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.spotlight").withStyle(ChatFormatting.GRAY));
    }
}
