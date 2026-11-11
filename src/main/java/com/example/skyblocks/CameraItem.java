package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A camera: right-click takes a photo of what you're looking at (without your hand or the HUD), printed on a sheet of
 * paper from your inventory - a Photo to hang on a wall or stand on a table (PhotoItem, PhotoFrameBlock).
 */
public class CameraItem extends Item {
    public CameraItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean paper = player.getAbilities().instabuild || player.getInventory().contains(new ItemStack(Items.PAPER));
        if (!paper) {
            if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.skyblocks.camera.paper").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            player.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.8F);
            com.example.skyblocks.client.ClientAccess.takePhoto(); // (client-only class)
        }
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.camera").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.camera.paper").withStyle(ChatFormatting.GRAY));
    }
}
