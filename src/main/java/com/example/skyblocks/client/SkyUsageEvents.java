package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** At the start of each frame, sky items in view of the player count as used (see SkyUsage). */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SkyUsageEvents {
    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        try {
            SkyUsage.markHeldItems();
        } catch (RuntimeException ignored) {
            // never let this get in the way of drawing the frame
        }
    }

    /** While the HUD is drawn, sky items in it (the hotbar) only need the hotbar's strip of their sky (SkyUsage). */
    @SubscribeEvent
    public static void onHudStart(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
        SkyUsage.inHud = true;
    }

    @SubscribeEvent
    public static void onHudEnd(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        SkyUsage.inHud = false;
    }

    private SkyUsageEvents() {}
}
