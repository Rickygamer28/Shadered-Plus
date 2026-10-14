package com.example.skyblocks.client.island;

import com.example.skyblocks.SkyBlocksMod;
import java.util.ArrayList;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.level.LevelEvent;

/** Keeps baked islands valid: rebuilt after a resource reload, freed when the client's world unloads. */
public final class IslandClientEvents {

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> IslandView.generation++);
        }

        private ModBus() {}
    }

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
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
