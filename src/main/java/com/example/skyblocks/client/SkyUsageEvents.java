package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** At the start of each frame, sky items in view of the player count as used (see SkyUsage). */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SkyUsageEvents {
    @SubscribeEvent
    public static void onFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        try {
            SkyUsage.markHeldItems();
        } catch (RuntimeException ignored) {
            // never let this get in the way of drawing the frame
        }
    }

    private SkyUsageEvents() {}
}
