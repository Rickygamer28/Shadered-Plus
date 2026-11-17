package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Magic chalk: right-click the top of a block - a chalk mark on it; sneaking, a rune (the middle of a magic circle).
 * Right-click a rune: its next rune. Right-click a mark, sneaking: it becomes a rune. Each mark or rune wears it down.
 */
public class MagicChalkItem extends Item {
    public MagicChalkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockState state = level.getBlockState(clicked);
        Player player = context.getPlayer();
        boolean sneaking = player != null && player.isShiftKeyDown();
        if (state.getBlock() instanceof ChalkRuneBlock) {
            // a rune: the next one
            if (!level.isClientSide) {
                int next = state.getValue(ChalkRuneBlock.RUNE) % 6 + 1;
                level.setBlock(clicked, state.setValue(ChalkRuneBlock.RUNE, next), 3);
                level.playSound(null, clicked, SoundEvents.CALCITE_HIT, SoundSource.BLOCKS, 0.8F, 1.5F);
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.skyblocks.chalk.rune",
                            Component.translatable("rune.skyblocks." + ChalkRuneBlockEntity.RUNES[next])).withStyle(ChatFormatting.AQUA), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos at;
        if (state.getBlock() instanceof ChalkMarkBlock) {
            if (!sneaking) {
                return InteractionResult.PASS;
            }
            at = clicked; // (a mark made a rune)
        } else {
            if (context.getClickedFace() != Direction.UP) {
                return InteractionResult.PASS;
            }
            at = clicked.above();
            if (!level.getBlockState(at).canBeReplaced() || !ChalkMarkBlock.supported(level, at)) {
                return InteractionResult.FAIL;
            }
        }
        if (!level.isClientSide) {
            BlockState put = sneaking ? ModBlocks.CHALK_RUNE.get().defaultBlockState() : ModBlocks.CHALK_MARK.get().defaultBlockState();
            level.setBlock(at, put, 3);
            level.playSound(null, at, SoundEvents.CALCITE_PLACE, SoundSource.BLOCKS, 0.7F, 1.6F);
            if (player != null && !player.getAbilities().instabuild) {
                context.getItemInHand().hurtAndBreak(1, player, context.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                        ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.magic_chalk").withStyle(ChatFormatting.GRAY));
    }
}
