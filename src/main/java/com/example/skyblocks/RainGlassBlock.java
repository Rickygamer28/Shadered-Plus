package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * Rain glass: rain running down a window. Works like the water caustics block: right-click it with a full block or a
 * Shadered sky block to see it behind the wet glass; sneak + right-click with an empty hand to take it out. Drawn by
 * client/WaterCausticsModel with the rain textures (tools/make_rain_glass.py).
 */
public class RainGlassBlock extends WaterCausticsBlock {
    public static final MapCodec<RainGlassBlock> CODEC = simpleCodec(RainGlassBlock::new);

    public RainGlassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.rain_glass").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.water_caustics_clear").withStyle(ChatFormatting.GRAY));
    }
}
