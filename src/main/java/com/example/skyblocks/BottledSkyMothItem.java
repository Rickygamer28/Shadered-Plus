package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** A Sky Moth in a bottle: use it on a block to let it out there (it stays near). */
public class BottledSkyMothItem extends Item {
    public BottledSkyMothItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel() instanceof ServerLevel level) {
            ItemStack stack = context.getItemInHand();
            Vec3 at = Vec3.atCenterOf(context.getClickedPos().relative(context.getClickedFace()));
            SkyCreatureSpawner.spawn(level, ModEntities.SKY_MOTH.get(), at, SkyCreatureSpawner.skyNamed(stack.getOrDefault(ModComponents.SKY_BRUSH.get(), "")), false);
            level.playSound(null, context.getClickedPos(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 1.5F);
            if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                stack.shrink(1);
                context.getPlayer().getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE));
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var sky = SkyCreatureSpawner.skyNamed(stack.getOrDefault(ModComponents.SKY_BRUSH.get(), ""));
        if (sky != null) {
            tooltip.add(Component.translatable(sky.getTranslationKey()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("tooltip.skyblocks.bottled_sky_moth").withStyle(ChatFormatting.GRAY));
    }
}
