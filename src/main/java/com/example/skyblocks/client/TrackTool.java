package com.example.skyblocks.client;

import com.example.skyblocks.ModItems;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TrackNetwork;
import com.example.skyblocks.TrackPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Track Tool in your hand: what you're pointing at (a node, a point along a piece, or a free spot - running the way
 * you face, to the nearest sixteenth of a turn), your start once picked, the piece you'd build to where you point now
 * (drawn by TrackRenderer, its length and cost or what's wrong with it over your hotbar), and right-clicks.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TrackTool {
    @Nullable
    static TrackPayloads.Spec start;
    /** Where the piece just built ended: once the track's back, the next one starts there. */
    @Nullable
    private static Vec3 chainAt;
    /** The piece you'd build now (for drawing), or null. */
    @Nullable
    static TrackPayloads.Resolved preview;

    private TrackTool() {}

    static boolean holding() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getMainHandItem().is(ModItems.TRACK_TOOL.get());
    }

    /** What you're pointing at. */
    @Nullable
    static TrackPayloads.Spec hover() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        TrackNetwork net = TrackNetwork.client;
        Vec3 loc = hit.getLocation();
        Vec3 q = hit.getDirection() == Direction.UP ? loc.add(0, 1.1, 0) : loc;
        TrackNetwork.Node node = net.nearestNode(q, 1.6);
        if (node != null) {
            return new TrackPayloads.Spec(1, node.id, 0, node.pos, node.dir);
        }
        TrackNetwork.Near near = net.nearest(q, 1.0);
        if (near != null && near.index() > 2 && near.index() < near.seg().pts.length - 3) {
            return new TrackPayloads.Spec(2, near.seg().id, near.index(), near.seg().pts[near.index()], near.seg().tan[near.index()]);
        }
        BlockPos b = hit.getDirection() == Direction.UP ? hit.getBlockPos().above() : hit.getBlockPos().relative(hit.getDirection());
        float yaw = Math.round(mc.player.getYRot() / 22.5F) * 22.5F * Mth.DEG_TO_RAD;
        return new TrackPayloads.Spec(0, 0, 0, new Vec3(b.getX() + 0.5, b.getY() + 0.1, b.getZ() + 0.5), new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)));
    }

    static boolean straightOn() {
        return Minecraft.getInstance().options.keySprint.isDown();
    }

    /** The track came back from the server: carry on from the piece just built. */
    public static void synced() {
        if (chainAt != null) {
            TrackNetwork.Node n = TrackNetwork.client.nearestNode(chainAt, 0.6);
            if (n != null) {
                start = new TrackPayloads.Spec(1, n.id, 0, n.pos, n.dir);
            }
            chainAt = null;
        }
        TrackRenderer.dirty = true;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        preview = null;
        if (!holding()) {
            if (mc.player == null || !mc.player.getMainHandItem().is(ModItems.TRACK_TOOL.get())) {
                start = null;
            }
            return;
        }
        TrackPayloads.Spec h = hover();
        if (start != null && start.at(TrackNetwork.client) == null) {
            start = null;
        }
        if (start == null) {
            if (mc.player.tickCount % 5 == 0) {
                mc.gui.setOverlayMessage(Component.translatable("hud.skyblocks.track.hint"), false);
            }
            return;
        }
        if (h != null) {
            preview = TrackPayloads.resolve(TrackNetwork.client, start, h, straightOn());
        }
        if (mc.player.tickCount % 3 == 0) {
            Component msg;
            if (preview == null) {
                msg = Component.translatable("hud.skyblocks.track.next");
            } else if (preview.plan().problem() != null) {
                msg = Component.translatable("message.skyblocks.track." + preview.plan().problem()).withStyle(ChatFormatting.RED);
            } else {
                msg = Component.translatable("hud.skyblocks.track.info", Math.round(preview.plan().length()), preview.plan().cost())
                        .withStyle(ChatFormatting.GREEN).append(Component.literal("  ")).append(Component.translatable("hud.skyblocks.track.next")
                                .withStyle(ChatFormatting.GRAY));
            }
            mc.gui.setOverlayMessage(msg, false);
        }
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || !holding() || mc.player == null) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(true);
        TrackPayloads.Spec h = hover();
        TrackNetwork net = TrackNetwork.client;
        if (mc.player.isShiftKeyDown()) {
            if (start != null) {
                start = null;
                mc.gui.setOverlayMessage(Component.translatable("message.skyblocks.track.cancelled"), false);
                return;
            }
            if (h != null) {
                Vec3 at = h.at(net);
                TrackNetwork.Near near = at == null ? null : net.nearest(at, 1.6);
                if (near != null) {
                    PacketDistributor.sendToServer(new TrackPayloads.Remove(near.seg().id));
                }
            }
            return;
        }
        if (h == null) {
            return;
        }
        if (start == null) {
            if (h.type() == 1 && net.nodes.get(h.id()) != null && net.nodes.get(h.id()).isSwitch()) {
                PacketDistributor.sendToServer(new TrackPayloads.Toggle(h.id()));
                return;
            }
            start = h;
            mc.gui.setOverlayMessage(Component.translatable("message.skyblocks.track.start"), false);
            return;
        }
        TrackPayloads.Resolved r = TrackPayloads.resolve(net, start, h, straightOn());
        if (r == null) {
            return;
        }
        if (r.plan().problem() != null) {
            mc.gui.setOverlayMessage(Component.translatable("message.skyblocks.track." + r.plan().problem()).withStyle(ChatFormatting.RED), false);
            return;
        }
        PacketDistributor.sendToServer(new TrackPayloads.Build(start, h, straightOn()));
        chainAt = r.p3();
        start = null;
    }
}
