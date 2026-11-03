package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A kaleidoscope: a brass-framed block with a window on every side, coloured glass turning inside
 * (client/KaleidoscopeRenderer). Right-click: its symmetry; sneak + right-click: its colours; a redstone signal stops it.
 */
public class KaleidoscopeBlock extends BaseEntityBlock {
    public static final MapCodec<KaleidoscopeBlock> CODEC = simpleCodec(KaleidoscopeBlock::new);

    public KaleidoscopeBlock(Properties properties) {
        super(properties);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof KaleidoscopeBlockEntity be) {
            if (player.isShiftKeyDown()) {
                be.cyclePalette();
                player.displayClientMessage(Component.translatable("message.skyblocks.kaleidoscope.palette",
                        Component.translatable("message.skyblocks.kaleidoscope.palette." + KaleidoscopeBlockEntity.PALETTES[be.palette()]))
                        .withStyle(ChatFormatting.GOLD), true);
            } else {
                be.cycleSymmetry();
                player.displayClientMessage(Component.translatable("message.skyblocks.kaleidoscope.symmetry",
                        KaleidoscopeBlockEntity.FOLDS[be.symmetry()]).withStyle(ChatFormatting.GOLD), true);
            }
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.8F + 0.1F * be.symmetry());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof KaleidoscopeBlockEntity be) {
            be.setPowered(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof KaleidoscopeBlockEntity be) {
            be.setPowered(level.hasNeighborSignal(pos));
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KaleidoscopeBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.kaleidoscope").withStyle(ChatFormatting.GRAY));
    }
}
