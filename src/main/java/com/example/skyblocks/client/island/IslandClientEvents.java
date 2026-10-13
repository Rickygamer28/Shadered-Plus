package com.example.skyblocks.client.island;

import com.example.skyblocks.SkyBlocksMod;
import java.util.ArrayList;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Keeps baked islands valid: rebuilt after a resource reload, freed when the client's world unloads. */
public final class IslandClientEvents {

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> IslandView.generation++);
        }

        private ModBus() {}
    }

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload event) {
            if (event.getLevel().isClientSide()) {
                for (IslandView view : new ArrayList<>(IslandView.LIVE)) {
                    view.close();
                }
            }
        }

        private GameBus() {}
    }

    private IslandClientEvents() {}
}
