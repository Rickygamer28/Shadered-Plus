package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Between the two cabinets of a versus match, through the server: the buttons held at a frame, the fight's number at
 * a frame, a piece of the fight, asking for it (client/arcade/Netplay). Handled as it arrives - not waiting for the
 * next tick, on the server or the client - so it gets across as fast as the connection lets it.
 */
public record ArcadeNetPayload(int match, int kind, int frame, int x, int y, byte[] data) implements CustomPacketPayload {
    public static final Type<ArcadeNetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "arcade_net"));

    public static final StreamCodec<ByteBuf, ArcadeNetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ArcadeNetPayload::match,
            ByteBufCodecs.VAR_INT, ArcadeNetPayload::kind,
            ByteBufCodecs.VAR_INT, ArcadeNetPayload::frame,
            ByteBufCodecs.INT, ArcadeNetPayload::x,
            ByteBufCodecs.VAR_INT, ArcadeNetPayload::y,
            ByteBufCodecs.byteArray(32000), ArcadeNetPayload::data,
            ArcadeNetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** (on the network's thread) */
    public static void handle(ArcadeNetPayload payload, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            if (context.player() instanceof ServerPlayer player) ArcadeVersus.relay(player, payload);
        } else {
            com.example.skyblocks.client.arcade.Netplay.deliver(payload.match(), new com.example.skyblocks.client.arcade.Netplay.Msg(
                    payload.kind(), payload.frame(), payload.x(), payload.y(), payload.data())); // (client-only class)
        }
    }
}
