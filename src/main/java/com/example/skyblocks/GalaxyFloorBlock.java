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
 * Galaxy floor: a slowly flowing nebula with twinkling stars (animated textures, tools/make_galaxy_floor.py). Works like
 * the water caustics block: right-click it with a block or a Shadered sky block to put it under the nebula.
 */
public class GalaxyFloorBlock extends WaterCausticsBlock {
    public static final MapCodec<GalaxyFloorBlock> GALAXY_CODEC = simpleCodec(GalaxyFloorBlock::new);

    public GalaxyFloorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return GALAXY_CODEC;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.galaxy_floor").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.water_caustics_clear").withStyle(ChatFormatting.GRAY));
    }
}
