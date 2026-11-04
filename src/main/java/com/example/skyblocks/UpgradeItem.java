package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** An upgrade for a singularity's containment chamber: put in an upgrade frame (SpecialFrameBlock). */
public class UpgradeItem extends Item {
    public enum Type { SPEED, RANGE, SILENT }

    private final Type type;

    public UpgradeItem(Properties properties, Type type) {
        super(properties);
        this.type = type;
    }

    public Type type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks." + type.name().toLowerCase(java.util.Locale.ROOT) + "_upgrade").withStyle(ChatFormatting.GRAY));
    }
}
