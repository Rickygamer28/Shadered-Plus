package com.example.skyblocks.client.tv;

import com.example.skyblocks.ModComponents;
import com.example.skyblocks.ModItems;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TabletState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** Someone listening to a song on a tablet: the line being sung floats above their head, for everyone near to see. */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TabletLyrics {
    @SubscribeEvent
    public static void onRender(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity e = event.getEntity();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = e.getItemInHand(hand);
            if (!stack.is(ModItems.TABLET.get())) continue;
            TabletState s = stack.get(ModComponents.TABLET.get());
            VideoPlayer p = s == null || s.source().isEmpty() ? null : TvPlayers.tablet(s.id());
            Music m = p == null ? null : p.music();
            if (m == null || p.state() != VideoPlayer.State.PLAYING) continue;
            String line = m.lineAt(p.position());
            if (line.isEmpty()) continue;
            line = "♪ " + line + " ♪";
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(0.0F, e.getBbHeight() + (e.shouldShowName() ? 0.8F : 0.55F), 0.0F);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(0.025F, -0.025F, 0.025F);
            float w = font.width(line);
            font.drawInBatch(line, -w / 2, 0, 0xFFFFFFFF, false, pose.last().pose(), event.getMultiBufferSource(), Font.DisplayMode.NORMAL, 0x55000000,
                    event.getPackedLight());
            pose.popPose();
            return;
        }
    }

    private TabletLyrics() {}
}
