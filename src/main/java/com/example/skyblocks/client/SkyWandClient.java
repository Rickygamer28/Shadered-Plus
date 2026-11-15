package com.example.skyblocks.client;

import com.example.skyblocks.EraseTrailPayload;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SkyTrailEntity;
import com.example.skyblocks.SkyWandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** A left-click with the sky wand at a line you drew: it's rubbed out (EraseTrailPayload), instead of hitting what's behind. */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SkyWandClient {
    private SkyWandClient() {}

    @SubscribeEvent
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (!event.isAttack() || player == null || mc.level == null || !(player.getMainHandItem().getItem() instanceof SkyWandItem)) {
            return;
        }
        Vec3 eye = player.getEyePosition(), dir = player.getLookAngle();
        for (SkyTrailEntity line : mc.level.getEntitiesOfClass(SkyTrailEntity.class, player.getBoundingBox().inflate(24.0))) {
            if (line.near(eye, dir, 24.0, 0.5)) {
                PacketDistributor.sendToServer(EraseTrailPayload.INSTANCE);
                event.setSwingHand(true);
                event.setCanceled(true);
                return;
            }
        }
    }
}
