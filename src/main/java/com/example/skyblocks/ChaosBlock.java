package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * The chaos block: its faces show Draconic Evolution's chaos shader (adapted with credit: see the tooltip, CREDITS.md
 * and assets/skyblocks/shaders/), drawn by client/ChaosBlockRenderer in the world and client/ChaosItemRenderer in hand.
 */
public class ChaosBlock extends Block {
    public ChaosBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.draconic_credit").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
