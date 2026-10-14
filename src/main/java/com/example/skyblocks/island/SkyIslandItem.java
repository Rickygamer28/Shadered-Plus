package com.example.skyblocks.island;

import com.example.skyblocks.ModComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** The Sky Island item: holds a scanned island (made by the Island Scanner); place it to show the miniature. */
public class SkyIslandItem extends BlockItem {
    public SkyIslandItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        IslandData island = stack.get(ModComponents.ISLAND.get());
        if (island == null) {
            tooltip.add(Component.translatable("tooltip.skyblocks.island_empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.skyblocks.island_size",
                    island.size(), island.size(), island.height()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.skyblocks.island_mobs", island.mobs()).withStyle(ChatFormatting.GRAY));
            if (island.hasOrigin()) {
                net.minecraft.core.BlockPos o = island.origin();
                String dim = island.dimension();
                tooltip.add(Component.translatable("tooltip.skyblocks.island_origin", o.getX(), o.getY(), o.getZ(),
                        dim.startsWith("minecraft:") ? dim.substring("minecraft:".length()) : dim).withStyle(ChatFormatting.DARK_GRAY));
                if (island.scannedDay() >= 0) {
                    tooltip.add(Component.translatable("tooltip.skyblocks.island_day", island.scannedDay()).withStyle(ChatFormatting.DARK_GRAY));
                }
                tooltip.add(Component.translatable("tooltip.skyblocks.island_travel").withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
    }
}
