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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Shader Light (from The Valley Mod by D0nkey100, used with permission): a lamp whose light has a colour - everything
 * within its reach takes the colour, most near the lamp, none at the edge, several lamps mixing (client/ShaderLightRenderer,
 * shaders/core/shader_light). Right-click with a dye for its colour; right-click with an empty hand to change its reach
 * (and how bright its ordinary light is), 1 to 15 blocks.
 */
public class ShaderLightBlock extends BaseEntityBlock {
    public static final MapCodec<ShaderLightBlock> CODEC = simpleCodec(ShaderLightBlock::new);
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 15);

    public ShaderLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 12));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye) || !(level.getBlockEntity(pos) instanceof ShaderLightBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            be.setColour(dye.getDyeColor().getTextureDiffuseColor() & 0xFFFFFF);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int reach = state.getValue(LEVEL);
        int next = player.isShiftKeyDown() ? (reach <= 1 ? 15 : reach - 1) : (reach >= 15 ? 1 : reach + 1);
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(LEVEL, next), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.6F, 0.6F + next / 25.0F);
            player.displayClientMessage(Component.translatable("message.skyblocks.shader_light.reach", next), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShaderLightBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.shader_light").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.tvm_credit").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
    }
}
