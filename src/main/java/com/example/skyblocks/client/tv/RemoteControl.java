package com.example.skyblocks.client.tv;

import com.example.skyblocks.ModItems;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TelevisionBlock;
import com.example.skyblocks.TelevisionBlockEntity;
import com.example.skyblocks.TelevisionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** The remote, pointed: the television (or projector) it's at, up to 48 blocks away. */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class RemoteControl {
    public static final double REACH = 48;

    /** The one pointed at (its wall's first block), or null. */
    @Nullable
    public static BlockPos aimed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        HitResult hit = mc.player.pick(REACH, 0, false);
        if (hit instanceof BlockHitResult bh && mc.level.getBlockEntity(bh.getBlockPos()) instanceof TelevisionBlockEntity) {
            return TelevisionBlock.anchor(mc.level, bh.getBlockPos());
        }
        return null;
    }

    public static void use(boolean sneaking) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos at = aimed();
        if (at == null) {
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("Point it at a television"), true);
            return;
        }
        if (sneaking) {
            if (mc.level.getBlockEntity(at) instanceof TelevisionBlockEntity tv && !tv.source().isEmpty()) {
                PacketDistributor.sendToServer(new TelevisionPayload(at, tv.paused() ? TelevisionPayload.PLAY : TelevisionPayload.PAUSE, "", 0));
                mc.player.displayClientMessage(Component.literal(tv.paused() ? "Play" : "Pause"), true);
            }
        } else {
            com.example.skyblocks.client.ClientAccess.openTelevision(at);
        }
    }

    /** Sneaking with the remote, the wheel goes through the channels of the one pointed at. */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.player.isShiftKeyDown()
                || !(mc.player.getMainHandItem().is(ModItems.REMOTE.get()) || mc.player.getOffhandItem().is(ModItems.REMOTE.get()))) {
            return;
        }
        BlockPos at = aimed();
        if (at == null) return;
        int dir = event.getScrollDeltaY() > 0 ? 1 : -1;
        PacketDistributor.sendToServer(new TelevisionPayload(at, TelevisionPayload.CHANNEL_STEP, "", dir));
        if (mc.level.getBlockEntity(at) instanceof TelevisionBlockEntity tv) {
            mc.player.displayClientMessage(Component.literal("Channel " + (dir > 0 ? "up" : "down")), true);
        }
        event.setCanceled(true);
    }

    private RemoteControl() {}
}
