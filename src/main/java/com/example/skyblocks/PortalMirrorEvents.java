package com.example.skyblocks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;

/** A player joins a world (logs in, changes dimension, respawns): its portal mirrors sent to them. */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalMirrorEvents {
    private PortalMirrorEvents() {}

    private static void send(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer sp) {
            SkyBlocksMod.NETWORK.send(PacketDistributor.PLAYER.with(() -> sp), PortalMirrors.Registry.of(sp.serverLevel()).payload());
        }
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        send(event.getEntity());
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        send(event.getEntity());
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        send(event.getEntity());
    }
}
