package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The singularity's menu over the network: what it holds (server -> client), what you clicked (client -> server). */
public final class SingularityPayloads {
    private SingularityPayloads() {}

    /** What it holds (each kind, count 1, and how many), what's marked to be destroyed, vacuum mode. */
    public record Contents(int containerId, List<ItemStack> items, List<Long> counts, List<String> destroyed, List<String> tags, boolean vacuum)
            implements CustomPacketPayload {
        public static final Type<Contents> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "singularity_contents"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Contents> STREAM_CODEC = StreamCodec.of((buf, c) -> {
            buf.writeVarInt(c.containerId);
            buf.writeVarInt(c.items.size());
            for (int i = 0; i < c.items.size(); i++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, c.items.get(i));
                buf.writeVarLong(c.counts.get(i));
            }
            buf.writeVarInt(c.destroyed.size());
            for (String s : c.destroyed) {
                buf.writeUtf(s);
            }
            buf.writeVarInt(c.tags.size());
            for (String s : c.tags) {
                buf.writeUtf(s);
            }
            buf.writeBoolean(c.vacuum);
        }, buf -> {
            int id = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), SingularityBlockEntity.MAX_KINDS);
            List<ItemStack> items = new ArrayList<>(n);
            List<Long> counts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
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
            return new Contents(id, items, counts, destroyed, tags, buf.readBoolean());
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Contents payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                Player player = context.player();
                if (player.containerMenu instanceof SingularityMenu menu && menu.containerId == payload.containerId()) {
                    menu.setContents(payload);
                }
            });
        }
    }

    /** A click in the menu: take (a stack / one / into your inventory), put in what you hold, mark to destroy, vacuum, a tag. */
    public record Action(int containerId, int action, ItemStack item, int button, String text) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "singularity_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of((buf, a) -> {
            buf.writeVarInt(a.containerId);
            buf.writeVarInt(a.action);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, a.item);
            buf.writeVarInt(a.button);
            buf.writeUtf(a.text, 256);
        }, buf -> new Action(buf.readVarInt(), buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readUtf(256)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Action payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                Player player = context.player();
                if (player.containerMenu instanceof SingularityMenu menu && menu.containerId == payload.containerId() && menu.stillValid(player)) {
                    menu.act(payload.action(), payload.item(), payload.button(), payload.text());
                }
            });
        }
    }
}
