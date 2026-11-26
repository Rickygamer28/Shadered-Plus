package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A tablet: a television in your hand (client/tv). Use it to open its window; what it plays shows on it, for you and
 * for everyone near you, and its sound comes from you.
 */
public class TabletItem extends Item {
    public TabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            com.example.skyblocks.client.ClientAccess.openTablet(hand); // (client-only class, never loaded on a server)
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !newStack.is(oldStack.getItem()); // (no bobbing every time what it plays changes)
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.tablet").withStyle(ChatFormatting.GRAY));
        TabletState s = stack.get(ModComponents.TABLET.get());
        if (s != null && !s.source().isEmpty()) {
            String src = s.source();
            tooltip.add(Component.literal(src.startsWith("file") ? "Playing a file" : src.length() > 48 ? src.substring(0, 48) + "..." : src)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
