package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** The singularity's menu over the network: what it holds (server -> client), what you clicked (client -> server). */
public final class SingularityPayloads {
    private SingularityPayloads() {}

    /**
     * What it holds (each kind, count 1, and how many), what's marked to be destroyed, vacuum mode - in pages (tens of
     * thousands of kinds don't fit one packet): kinds from {@code start}, {@code total} in all.
     */
    public record Contents(int containerId, int start, int total, List<ItemStack> items, List<Long> counts, List<String> destroyed, List<String> tags, boolean vacuum) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(containerId);
            buf.writeVarInt(start);
            buf.writeVarInt(total);
            buf.writeVarInt(items.size());
            for (int i = 0; i < items.size(); i++) {
                buf.writeItem(items.get(i)); // (1.20.1: an empty one too)
                buf.writeVarLong(counts.get(i));
            }
            buf.writeVarInt(destroyed.size());
            for (String s : destroyed) {
                buf.writeUtf(s);
            }
            buf.writeVarInt(tags.size());
            for (String s : tags) {
                buf.writeUtf(s);
            }
            buf.writeBoolean(vacuum);
        }

        public static Contents decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            int start = buf.readVarInt(), total = Math.min(buf.readVarInt(), SingularityBlockEntity.MAX_KINDS);
            int n = Math.min(buf.readVarInt(), SingularityBlockEntity.MAX_KINDS);
            List<ItemStack> items = new ArrayList<>(n);
            List<Long> counts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                items.add(buf.readItem());
                counts.add(buf.readVarLong());
            }
            int m = Math.min(buf.readVarInt(), 4096);
            List<String> destroyed = new ArrayList<>(m);
            for (int i = 0; i < m; i++) {
                destroyed.add(buf.readUtf());
            }
            int k = Math.min(buf.readVarInt(), SingularityBlockEntity.MAX_TAGS);
            List<String> tags = new ArrayList<>(k);
            for (int i = 0; i < k; i++) {
                tags.add(buf.readUtf());
            }
            return new Contents(id, start, total, items, counts, destroyed, tags, buf.readBoolean());
        }

        public static void handle(Contents payload, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.setPacketHandled(true);
            context.enqueueWork(() -> {
                Player player = com.example.skyblocks.client.ClientAccess.player(); // (client-only class: this runs on the client)
                if (player != null && player.containerMenu instanceof SingularityMenu menu && menu.containerId == payload.containerId()) {
                    menu.setContents(payload);
                }
            });
        }
    }

    /** A click in the menu: take (a stack / one / into your inventory), put in what you hold, mark to destroy, vacuum, a tag. */
    public record Action(int containerId, int action, ItemStack item, int button, String text) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(containerId);
            buf.writeVarInt(action);
            buf.writeItem(item);
            buf.writeVarInt(button);
            buf.writeUtf(text, 256);
        }

        public static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readVarInt(), buf.readVarInt(), buf.readItem(), buf.readVarInt(), buf.readUtf(256));
        }

        public static void handle(Action payload, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.setPacketHandled(true);
            context.enqueueWork(() -> {
                Player player = context.getSender();
                if (player != null && player.containerMenu instanceof SingularityMenu menu && menu.containerId == payload.containerId() && menu.stillValid(player)) {
                    menu.act(payload.action(), payload.item(), payload.button(), payload.text());
                }
            });
        }
    }
}
