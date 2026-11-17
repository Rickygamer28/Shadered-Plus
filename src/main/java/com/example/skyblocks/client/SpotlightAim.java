package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SpotlightBlockEntity;
import com.example.skyblocks.SpotlightPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aiming a spotlight (sneak + right-click it): until the next right-click it turns to where you're looking (the point
 * your crosshair's on, up to 64 blocks off), and that right-click leaves it pointing there. Sneak + right-click the
 * same spotlight instead: it follows players again. Given up if you go far from it or it's gone.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SpotlightAim {
    private static final double REACH = 64.0;
    private static BlockPos aiming;

    private SpotlightAim() {}

    public static void start(BlockPos pos) {
        aiming = pos.immutable();
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.skyblocks.spotlight_aiming"), true);
        }
    }

    public static boolean aimingAt(BlockPos pos) {
        return aiming != null && aiming.equals(pos);
    }

    /** The point the player's crosshair is on (or as far as it reaches). */
    public static Vec3 target(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return Vec3.ZERO;
        }
        Vec3 eye = player.getEyePosition(partialTick), look = player.getViewVector(partialTick);
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, eye.add(look.scale(REACH)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(aiming) ? hit.getLocation() : eye.add(look.scale(REACH));
    }

    /** Which way (yaw, pitch) from a spotlight's lamp to a point. */
    public static float[] towards(Vec3 lamp, Vec3 at) {
        Vec3 d = at.subtract(lamp);
        return new float[] {(float) Mth.atan2(d.x, d.z), (float) Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))};
    }

    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (aiming == null || !event.isUseItem()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        BlockPos pos = aiming;
        aiming = null;
        if (player == null || mc.level == null || !(mc.level.getBlockEntity(pos) instanceof SpotlightBlockEntity be)) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(true);
        if (!(be.getBlockState().getBlock() instanceof com.example.skyblocks.BeamLightBlock) && player.isShiftKeyDown() && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
            PacketDistributor.sendToServer(new SpotlightPayload(pos, true, 0.0F, 0.0F));
            player.displayClientMessage(Component.translatable("message.skyblocks.spotlight_follow"), true);
            return;
        }
        float[] way = towards(SpotlightRenderer.pivot(be), target(1.0F));
        PacketDistributor.sendToServer(new SpotlightPayload(pos, false, way[0], way[1]));
        player.displayClientMessage(Component.translatable("message.skyblocks.spotlight_aimed"), true);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (aiming == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.level.getBlockEntity(aiming) instanceof SpotlightBlockEntity)
                || mc.player.distanceToSqr(aiming.getCenter()) > 48.0 * 48.0) {
            aiming = null;
        }
    }
}
