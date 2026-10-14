package com.example.skyblocks.island;

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

    /** Drawn as its pedestal with the island floating above it (client/island/SkyIslandItemRenderer). */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new com.example.skyblocks.client.island.SkyIslandItemRenderer();
                }
                return renderer;
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        IslandData island = IslandData.get(stack);
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
