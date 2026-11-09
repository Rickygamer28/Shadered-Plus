package com.example.skyblocks;

import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Sneak + right-click with a block: a weather globe's base made of it (any wood), a lava lamp pillar's foot and cap
 * (any block). (The game would place the block instead - sneaking with something in hand skips the block's own use - so it's caught here first.)
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class WeatherGlobeEvents {
    private WeatherGlobeEvents() {}

    @SubscribeEvent
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown()) {
            return;
        }
        net.minecraft.world.level.block.Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        boolean globe = block instanceof WeatherGlobeBlock && WeatherGlobeBlock.baseBlock(event.getItemStack());
        boolean pillar = block instanceof LavaPillarBlock && LavaPillarBlock.baseBlock(event.getItemStack());
        if (block instanceof LavaPillarBlock && event.getItemStack().getItem() instanceof net.minecraft.world.item.DyeItem) {
            // (sneak + a dye: the pillar's liquid - sneaking with it in hand, the block's own use would be skipped)
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.TRUE);
            return;
        }
        if (!globe && !pillar) {
            return;
        }
        if (!event.getLevel().isClientSide) {
            if (globe) {
                WeatherGlobeBlock.setBase(event.getLevel(), event.getPos(), event.getItemStack());
            } else {
                LavaPillarBlock.setBase(event.getLevel(), event.getPos(), event.getItemStack());
            }
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }
}
