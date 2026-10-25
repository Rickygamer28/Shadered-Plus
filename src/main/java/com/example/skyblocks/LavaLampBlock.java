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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A lava lamp: blobs of glowing wax rising, stretching, breaking up and sinking in a glass of liquid (drawn by
 * client/LavaLampRenderer and shaders/core/lava_lamp). Right-click: on / off (off, the wax settles at the bottom);
 * with a dye: the wax's colour; sneak + right-click: RGB (cycling through the rainbow).
 */
public class LavaLampBlock extends BaseEntityBlock {
    public static final MapCodec<LavaLampBlock> CODEC = simpleCodec(LavaLampBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.box(4.5, 0.0, 4.5, 11.5, 15.5, 11.5);

    public LavaLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye) || !(level.getBlockEntity(pos) instanceof GlowColourBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            be.setColour(dye.getDyeColor().getTextureDiffuseColor());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                if (level.getBlockEntity(pos) instanceof GlowColourBlockEntity be) {
                    be.setRgb(!be.rgb());
                    player.displayClientMessage(Component.translatable(be.rgb() ? "message.skyblocks.rgb_on" : "message.skyblocks.rgb_off")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
            } else {
                level.setBlock(pos, state.cycle(LIT), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, state.getValue(LIT) ? 0.7F : 1.0F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return GlowColourBlockEntity.lavaLamp(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.lava_lamp").withStyle(ChatFormatting.GRAY));
    }
}
