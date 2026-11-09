package com.example.skyblocks;

import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Sneak + right-click on a weather globe with a block: its base made of that block. (The game would place the block
 * instead - sneaking with something in hand skips the block's own use - so it's caught here first.)
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class WeatherGlobeEvents {
    private WeatherGlobeEvents() {}

    @SubscribeEvent
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown() || !WeatherGlobeBlock.baseBlock(event.getItemStack())
                || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof WeatherGlobeBlock)) {
            return;
        }
        if (!event.getLevel().isClientSide) {
            WeatherGlobeBlock.setBase(event.getLevel(), event.getPos(), event.getItemStack());
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }
}
