package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * A speaker: its cone thumps with the bass of the music playing nearby (client/SpeakerRenderer), and it carries the
 * music's reaction further: a speaker within 24 blocks of a playing jukebox makes everything near the speaker react
 * as if the jukebox were there.
 */
public class SpeakerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public SpeakerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Right-click with a dye: the light's colour (ends RGB mode). */
    protected com.example.skyblocks.compat.ItemInteractionResult useItemOn121(ItemStack stack, BlockState state, net.minecraft.world.level.Level level,
                                                                  BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                  net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.DyeItem dye) || !(level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker)) {
            return com.example.skyblocks.compat.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            speaker.setColour(com.example.skyblocks.compat.Compat.dyeColour(dye.getDyeColor()));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.DYE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return com.example.skyblocks.compat.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Sneak + right-click with an empty hand: RGB mode on / off. */
    protected net.minecraft.world.InteractionResult useWithoutItem121(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                                                                   net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            speaker.setRgb(!speaker.rgb());
            player.displayClientMessage(Component.translatable(speaker.rgb() ? "message.skyblocks.speaker_rgb_on" : "message.skyblocks.speaker_rgb_off")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), net.minecraft.sounds.SoundSource.BLOCKS, 0.6F,
                    speaker.rgb() ? 1.4F : 0.8F);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.speaker").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.speaker_colour").withStyle(ChatFormatting.GRAY));
    }

    /** 1.20.1: one use() - an item first (useItemOn121), then the empty-hand use (useWithoutItem121), as 1.21 does. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                                      net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                                      net.minecraft.world.phys.BlockHitResult hit) {
        com.example.skyblocks.compat.ItemInteractionResult r = useItemOn121(player.getItemInHand(hand), state, level, pos, player, hand, hit);
        if (r != com.example.skyblocks.compat.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return r.result();
        }
        return hand == net.minecraft.world.InteractionHand.MAIN_HAND ? useWithoutItem121(state, level, pos, player, hit) : net.minecraft.world.InteractionResult.PASS;
    }
}
