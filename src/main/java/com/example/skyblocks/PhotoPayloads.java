package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Photos over the network: a picture just taken (to the server), asking for one, and one sent (to a client). */
public final class PhotoPayloads {
    private PhotoPayloads() {}

    /** Each player's last photo (game time): one a second at most. */
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    /** A picture coming in pieces from each player: which take, the pieces so far. */
    private static final class Incoming {
        final int take;
        final byte[][] pieces;
        int got, bytes;

        Incoming(int take, int total) {
            this.take = take;
            this.pieces = new byte[total][];
        }
    }

    private static final Map<UUID, Incoming> INCOMING = new ConcurrentHashMap<>();

    /** Player -> server: piece {@code index} of {@code total} of the picture their camera just took (take: which photo). */
    public record Take(int take, int index, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<Take> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "photo_take"));
        public static final StreamCodec<ByteBuf, Take> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Take::take,
                ByteBufCodecs.VAR_INT, Take::index, ByteBufCodecs.VAR_INT, Take::total, ByteBufCodecs.byteArray(PhotoStore.PIECE), Take::data, Take::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Take m, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer p) || m.total <= 0 || m.total > PhotoStore.MAX_BYTES / PhotoStore.PIECE + 1
                        || m.index < 0 || m.index >= m.total) {
                    return;
                }
                Incoming in = INCOMING.get(p.getUUID());
                if (in == null || in.take != m.take || in.pieces.length != m.total) {
                    in = new Incoming(m.take, m.total);
                    INCOMING.put(p.getUUID(), in);
                }
                if (in.pieces[m.index] == null) {
                    in.pieces[m.index] = m.data;
                    in.got++;
                    in.bytes += m.data.length;
                }
                if (in.bytes > PhotoStore.MAX_BYTES) {
                    INCOMING.remove(p.getUUID());
                    return;
                }
                if (in.got < in.pieces.length) {
                    return;
                }
                INCOMING.remove(p.getUUID());
                byte[] all = new byte[in.bytes];
                int at = 0;
                for (byte[] piece : in.pieces) {
                    System.arraycopy(piece, 0, all, at, piece.length);
                    at += piece.length;
                }
                print(p, all);
            });
        }
    }

    /** A whole picture in: printed, if they've a camera in hand, paper, and haven't just taken one. */
    private static void print(ServerPlayer p, byte[] data) {
        if (data.length == 0 || cameraHand(p) == null) {
            return;
        }
        long now = p.serverLevel().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < 15) {
            return;
        }
        if (!p.getAbilities().instabuild) { // (a sheet of paper for each print)
            int slot = p.getInventory().findSlotMatchingItem(new ItemStack(Items.PAPER));
            if (slot < 0) {
                p.displayClientMessage(Component.translatable("message.skyblocks.camera.paper").withStyle(ChatFormatting.RED), true);
                return;
            }
            p.getInventory().removeItem(slot, 1);
        }
        LAST.put(p.getUUID(), now);
        int id = PhotoStore.get(p.server).add(data);
        ItemStack photo = new ItemStack(ModItems.PHOTO.get());
        photo.set(ModComponents.PHOTO.get(), new ModComponents.Photo(id, p.getGameProfile().getName(), p.serverLevel().getDayTime() / 24000L + 1));
        if (!p.getInventory().add(photo)) {
            p.drop(photo, false);
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    /** Client -> server: send me photo number id. */
    public record Want(int id) implements CustomPacketPayload {
        public static final Type<Want> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "photo_want"));
        public static final StreamCodec<ByteBuf, Want> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Want::new, Want::id);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Want m, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer p) {
                    byte[] data = PhotoStore.get(p.server).get(m.id);
                    PacketDistributor.sendToPlayer(p, new Picture(m.id, data == null ? new byte[0] : data));
                }
            });
        }
    }

    /** Server -> client: photo number id (empty: there's none). */
    public record Picture(int id, byte[] data) implements CustomPacketPayload {
        public static final Type<Picture> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "photo_picture"));
        public static final StreamCodec<ByteBuf, Picture> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Picture::id,
                ByteBufCodecs.byteArray(PhotoStore.MAX_BYTES), Picture::data, Picture::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Picture m, IPayloadContext context) {
            context.enqueueWork(() -> com.example.skyblocks.client.ClientAccess.photo(m.id, m.data)); // (client-only class)
        }
    }

    /** The hand holding a camera (main first), or null. */
    static InteractionHand cameraHand(net.minecraft.world.entity.player.Player p) {
        return p.getMainHandItem().getItem() instanceof CameraItem ? InteractionHand.MAIN_HAND
                : p.getOffhandItem().getItem() instanceof CameraItem ? InteractionHand.OFF_HAND : null;
    }
}
